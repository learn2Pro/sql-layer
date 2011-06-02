/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.sql.pg;

import com.akiban.sql.StandardException;
import com.akiban.sql.parser.SQLParser;
import com.akiban.sql.parser.StatementNode;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.Column;
import com.akiban.ais.model.UserTable;
import com.akiban.server.api.DDLFunctions;
import com.akiban.server.service.instrumentation.PostgresSessionTracer;
import com.akiban.server.service.instrumentation.SessionTracer;
import com.akiban.server.service.ServiceManager;
import com.akiban.server.service.ServiceManagerImpl;
import com.akiban.server.service.session.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.io.*;
import java.util.*;

/**
 * Connection to a Postgres server client.
 * Runs in its own thread; has its own AkServer Session.
 *
 */
public class PostgresServerConnection implements PostgresServerSession, Runnable
{
    private static final Logger logger = LoggerFactory.getLogger(PostgresServerConnection.class);

    private PostgresServer server;
    private boolean running = false, ignoreUntilSync = false;
    private Socket socket;
    private PostgresMessenger messenger;
    private int pid, secret;
    private int version;
    private Properties properties;
    private Map<String,Object> attributes = new HashMap<String,Object>();
    private Map<String,PostgresStatement> preparedStatements =
        new HashMap<String,PostgresStatement>();
    private Map<String,PostgresStatement> boundPortals =
        new HashMap<String,PostgresStatement>();

    private Session session;
    private ServiceManager serviceManager;
    private int aisGeneration = -1;
    private AkibanInformationSchema ais;
    private String defaultSchemaName;
    private SQLParser parser;
    private PostgresStatementCache statementCache;
    private PostgresStatementParser[] unparsedGenerators;
    private PostgresStatementGenerator[] parsedGenerators;
    private Thread thread;
    
    private boolean instrumentationEnabled = false;
    private String sql;
    private PostgresSessionTracer sessionTracer;

    public PostgresServerConnection(PostgresServer server, Socket socket, 
                                    int pid, int secret) {
        this.server = server;
        this.socket = socket;
        this.pid = pid;
        this.secret = secret;
        this.sessionTracer = ServiceManagerImpl.get().getInstrumentationService().createSqlSessionTracer(pid);
        sessionTracer.setRemoteAddress(socket.getInetAddress().getHostAddress());
    }

    public void start() {
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    public void stop() {
        running = false;
        // Can only wake up stream read by closing down socket.
        try {
            socket.close();
        }
        catch (IOException ex) {
        }
        if ((thread != null) && (thread != Thread.currentThread())) {
            try {
                // Wait a bit, but don't hang up shutdown if thread is wedged.
                thread.join(500);
            }
            catch (InterruptedException ex) {
            }
            thread = null;
        }
    }

    public void run() {
        try {
            // We flush() when we mean it. 
            // So, turn off kernel delay, but wrap a buffer so every
            // message isn't its own packet.
            socket.setTcpNoDelay(true);
            messenger = new PostgresMessenger(socket.getInputStream(),
                                              new BufferedOutputStream(socket.getOutputStream()));
            topLevel();
        }
        catch (Exception ex) {
            if (running)
                logger.warn("Error in server", ex);
        }
        finally {
            try {
                socket.close();
            }
            catch (IOException ex) {
            }
        }
    }

    protected enum ErrorMode { NONE, SIMPLE, EXTENDED };

    protected void topLevel() throws IOException, StandardException {
        logger.info("Connect from {}" + socket.getRemoteSocketAddress());
        boolean startupComplete = false;
        while (running) {
            int type = messenger.readMessage(startupComplete);
            if (ignoreUntilSync) {
                if ((type != -1) && (type != PostgresMessenger.SYNC_TYPE))
                    continue;
                ignoreUntilSync = false;
            }
            ErrorMode errorMode = ErrorMode.NONE;
            try {
                sessionTracer.beginEvent("sql: process");
                switch (type) {
                case -1:                                    // EOF
                    stop();
                    break;
                case PostgresMessenger.SYNC_TYPE:
                    readyForQuery();
                    break;
                case PostgresMessenger.STARTUP_MESSAGE_TYPE:
                    startupComplete = processStartupMessage();
                    break;
                case PostgresMessenger.PASSWORD_MESSAGE_TYPE:
                    processPasswordMessage();
                    break;
                case PostgresMessenger.QUERY_TYPE:
                    errorMode = ErrorMode.SIMPLE;
                    processQuery();
                    break;
                case PostgresMessenger.PARSE_TYPE:
                    errorMode = ErrorMode.EXTENDED;
                    processParse();
                    break;
                case PostgresMessenger.BIND_TYPE:
                    errorMode = ErrorMode.EXTENDED;
                    processBind();
                    break;
                case PostgresMessenger.DESCRIBE_TYPE:
                    errorMode = ErrorMode.EXTENDED;
                    processDescribe();
                    break;
                case PostgresMessenger.EXECUTE_TYPE:
                    errorMode = ErrorMode.EXTENDED;
                    processExecute();
                    break;
                case PostgresMessenger.CLOSE_TYPE:
                    processClose();
                    break;
                case PostgresMessenger.TERMINATE_TYPE:
                    processTerminate();
                    break;
                default:
                    throw new IOException("Unknown message type: " + (char)type);
                }
            }
            catch (StandardException ex) {
                logger.warn("Error in query", ex);
                if (errorMode == ErrorMode.NONE) throw ex;
                {
                    messenger.beginMessage(PostgresMessenger.ERROR_RESPONSE_TYPE);
                    messenger.write('S');
                    messenger.writeString("ERROR");
                    // TODO: Could dummy up an SQLSTATE, etc.
                    messenger.write('M');
                    messenger.writeString(ex.getMessage());
                    messenger.write(0);
                    messenger.sendMessage(true);
                }
                if (errorMode == ErrorMode.EXTENDED)
                    ignoreUntilSync = true;
                else
                    readyForQuery();
            }
            finally {
                sessionTracer.endEvent();
            }
        }
        server.removeConnection(pid);
    }

    protected void readyForQuery() throws IOException {
        messenger.beginMessage(PostgresMessenger.READY_FOR_QUERY_TYPE);
        messenger.writeByte('I'); // Idle ('T' -> xact open; 'E' -> xact abort)
        messenger.sendMessage(true);
    }

    protected boolean processStartupMessage() throws IOException, StandardException {
        int version = messenger.readInt();
        switch (version) {
        case PostgresMessenger.VERSION_CANCEL:
            processCancelRequest();
            return false;
        case PostgresMessenger.VERSION_SSL:
            processSSLMessage();
            return false;
        default:
            this.version = version;
            logger.debug("Version {}.{}", (version >> 16), (version & 0xFFFF));
        }

        properties = new Properties();
        while (true) {
            String param = messenger.readString();
            if (param.length() == 0) break;
            String value = messenger.readString();
            properties.put(param, value);
        }
        logger.debug("Properties: {}", properties);
        String enc = properties.getProperty("client_encoding");
        if (enc != null) {
            if ("UNICODE".equals(enc))
                messenger.setEncoding("UTF-8");
            else
                messenger.setEncoding(enc);
        }

        // Get initial version of AIS.
        session = ServiceManagerImpl.newSession();
        serviceManager = ServiceManagerImpl.get();
        updateAIS();

        {
            messenger.beginMessage(PostgresMessenger.AUTHENTICATION_TYPE);
            messenger.writeInt(PostgresMessenger.AUTHENTICATION_CLEAR_TEXT);
            messenger.sendMessage(true);
        }
        return true;
    }

    protected void processCancelRequest() throws IOException {
        int pid = messenger.readInt();
        int secret = messenger.readInt();
        PostgresServerConnection connection = server.getConnection(pid);
        if ((connection != null) && (secret == connection.secret))
            // No easy way to signal in another thread.
            connection.messenger.setCancel(true);
        stop();                                         // That's all for this connection.
    }

    protected void processSSLMessage() throws IOException, StandardException {
        OutputStream raw = messenger.getOutputStream();
        raw.write('N');         // No SSL support.
        raw.flush();
    }

    protected void processPasswordMessage() throws IOException {
        String user = properties.getProperty("user");
        String pass = messenger.readString();
        logger.info("Login {}/{}", user, pass);
        Properties status = new Properties();
        // This is enough to make the JDBC driver happy.
        status.put("client_encoding", properties.getProperty("client_encoding", "UNICODE"));
        status.put("server_encoding", messenger.getEncoding());
        status.put("server_version", "8.4.7"); // Not sure what the min it'll accept is.
        status.put("session_authorization", user);
        
        {
            messenger.beginMessage(PostgresMessenger.AUTHENTICATION_TYPE);
            messenger.writeInt(PostgresMessenger.AUTHENTICATION_OK);
            messenger.sendMessage();
        }
        for (String prop : status.stringPropertyNames()) {
            messenger.beginMessage(PostgresMessenger.PARAMETER_STATUS_TYPE);
            messenger.writeString(prop);
            messenger.writeString(status.getProperty(prop));
            messenger.sendMessage();
        }
        {
            messenger.beginMessage(PostgresMessenger.BACKEND_KEY_DATA_TYPE);
            messenger.writeInt(pid);
            messenger.writeInt(secret);
            messenger.sendMessage();
        }
        readyForQuery();
    }

    protected void processQuery() throws IOException, StandardException {
        sql = messenger.readString();
        sessionTracer.setCurrentStatement(sql);
        logger.info("Query: {}", sql);

        updateAIS();

        PostgresStatement pstmt = null;
        if (statementCache != null)
            pstmt = statementCache.get(sql);
        if (pstmt == null) {
            for (PostgresStatementParser parser : unparsedGenerators) {
                // Try special recognition first; only allowed to turn
                // into one statement.
                pstmt = parser.parse(this, sql, null);
                if (pstmt != null)
                    break;
            }
        }
        if (pstmt != null) {
            pstmt.sendDescription(this, false);
            try {
                sessionTracer.beginEvent("sql: execute");
                pstmt.execute(this, -1);
            }
            finally {
                sessionTracer.endEvent();
            }
        }
        else {
            // Parse as a _list_ of statements and process each in turn.
            List<StatementNode> stmts;
            try {
                sessionTracer.beginEvent("sql: parse");
                stmts = parser.parseStatements(sql);
            }
            finally {
                sessionTracer.endEvent();
            }
            for (StatementNode stmt : stmts) {
                pstmt = generateStatement(stmt, null);
                if ((statementCache != null) && (stmts.size() == 1))
                    statementCache.put(sql, pstmt);
                pstmt.sendDescription(this, false);
                try {
                    sessionTracer.beginEvent("sql: execute");
                    pstmt.execute(this, -1);
                }
                finally {
                    sessionTracer.endEvent();
                }
            }
        }
        readyForQuery();
        logger.debug("Query complete");
    }

    protected void processParse() throws IOException, StandardException {
        String stmtName = messenger.readString();
        sql = messenger.readString();
        short nparams = messenger.readShort();
        int[] paramTypes = new int[nparams];
        for (int i = 0; i < nparams; i++)
            paramTypes[i] = messenger.readInt();
        logger.info("Parse: {}", sql);

        updateAIS();

        PostgresStatement pstmt = null;
        if (statementCache != null)
            pstmt = statementCache.get(sql);
        if (pstmt == null) {
            StatementNode stmt;
            try {
                sessionTracer.beginEvent("sql: parse");
                stmt = parser.parseStatement(sql);
            }
            finally {
                sessionTracer.endEvent();
            }
            pstmt = generateStatement(stmt, paramTypes);
            if (statementCache != null)
                statementCache.put(sql, pstmt);
        }
        preparedStatements.put(stmtName, pstmt);
        messenger.beginMessage(PostgresMessenger.PARSE_COMPLETE_TYPE);
        messenger.sendMessage();
    }

    protected void processBind() throws IOException, StandardException {
        String portalName = messenger.readString();
        String stmtName = messenger.readString();
        String[] params = null;
        {
            boolean[] paramsBinary = null;
            short nformats = messenger.readShort();
            if (nformats > 0) {
                paramsBinary = new boolean[nformats];
                for (int i = 0; i < nformats; i++)
                    paramsBinary[i] = (messenger.readShort() == 1);
            }
            short nparams = messenger.readShort();
            if (nparams > 0) {
                params = new String[nparams];
                boolean binary = false;
                for (int i = 0; i < nparams; i++) {
                    if (i < nformats)
                        binary = paramsBinary[i];
                    int len = messenger.readInt();
                    if (len < 0) continue;      // Null
                    byte[] param = new byte[len];
                    messenger.readFully(param, 0, len);
                    if (binary) {
                        throw new IOException("Don't know how to parse binary format.");
                    }
                    else {
                        params[i] = new String(param, messenger.getEncoding());
                    }
                }
            }
        }
        boolean[] resultsBinary = null; 
        boolean defaultResultsBinary = false;
        {        
            short nresults = messenger.readShort();
            if (nresults == 1)
                defaultResultsBinary = (messenger.readShort() == 1);
            else if (nresults > 0) {
                resultsBinary = new boolean[nresults];
                for (int i = 0; i < nresults; i++) {
                    resultsBinary[i] = (messenger.readShort() == 1);
                }
                defaultResultsBinary = resultsBinary[nresults-1];
            }
        }
        PostgresStatement pstmt = preparedStatements.get(stmtName);
        boundPortals.put(portalName, 
                         pstmt.getBoundStatement(params, 
                                                 resultsBinary, defaultResultsBinary));
        messenger.beginMessage(PostgresMessenger.BIND_COMPLETE_TYPE);
        messenger.sendMessage();
    }

    protected void processDescribe() throws IOException, StandardException {
        byte source = messenger.readByte();
        String name = messenger.readString();
        PostgresStatement pstmt;        
        switch (source) {
        case (byte)'S':
            pstmt = preparedStatements.get(name);
            break;
        case (byte)'P':
            pstmt = boundPortals.get(name);
            break;
        default:
            throw new IOException("Unknown describe source: " + (char)source);
        }
        pstmt.sendDescription(this, true);
    }

    protected void processExecute() throws IOException, StandardException {
        String portalName = messenger.readString();
        int maxrows = messenger.readInt();
        PostgresStatement pstmt = boundPortals.get(portalName);
        try {
            sessionTracer.beginEvent("sql: execute");
            pstmt.execute(this, maxrows);
        }
        finally {
            sessionTracer.endEvent();
        }
        logger.debug("Execute complete");
    }

    protected void processClose() throws IOException {
        byte source = messenger.readByte();
        String name = messenger.readString();
        PostgresStatement pstmt;        
        switch (source) {
        case (byte)'S':
            pstmt = preparedStatements.remove(name);
            break;
        case (byte)'P':
            pstmt = boundPortals.remove(name);
            break;
        default:
            throw new IOException("Unknown describe source: " + (char)source);
        }
        messenger.beginMessage(PostgresMessenger.CLOSE_COMPLETE_TYPE);
        messenger.sendMessage();
    }
    
    protected void processTerminate() throws IOException {
        stop();
    }

    // When the AIS changes, throw everything away, since it might
    // point to obsolete objects.
    protected void updateAIS() throws StandardException {
        DDLFunctions ddl = serviceManager.getDXL().ddlFunctions();
        // TODO: This could be more reliable if the AIS object itself
        // also knew its generation. Right now, can get new generation
        // # and old AIS and not notice until next change.
        int currentGeneration = ddl.getGeneration();
        if (aisGeneration == currentGeneration) 
            return;             // Unchanged.
        aisGeneration = currentGeneration;
        ais = ddl.getAIS(session);

        parser = new SQLParser();

        defaultSchemaName = getProperty("database");
        // Temporary until completely removed.
        boolean hapi = false;
        if (defaultSchemaName.startsWith("hapi.")) {
            defaultSchemaName = defaultSchemaName.substring(5);
            hapi = true;
        }
        // TODO: Any way / need to ask AIS if schema exists and report error?

        statementCache = server.getStatementCache(aisGeneration);
        unparsedGenerators = new PostgresStatementParser[] {
            new PostgresEmulatedMetaDataStatementParser(this)
        };
        parsedGenerators = new PostgresStatementGenerator[] {
            // Can be ordered by frequency so long as there is no overlap.
            (hapi) ? new PostgresHapiCompiler(this) : new PostgresOperatorCompiler(this),
            new PostgresDDLStatementGenerator(this),
            new PostgresSessionStatementGenerator(this),
            new PostgresExplainStatementGenerator(this)
        };
    }

    protected void sessionChanged() {
        if (parsedGenerators == null) return; // setAttribute() from generator's ctor.
        for (PostgresStatementParser parser : unparsedGenerators) {
            parser.sessionChanged(this);
        }
        for (PostgresStatementGenerator generator : parsedGenerators) {
            generator.sessionChanged(this);
        }
    }

    protected PostgresStatement generateStatement(StatementNode stmt, int[] paramTypes)
            throws StandardException {
        try {
            sessionTracer.beginEvent("sql: optimize");
            for (PostgresStatementGenerator generator : parsedGenerators) {
                PostgresStatement pstmt = generator.generate(this, stmt, paramTypes);
                if (pstmt != null) return pstmt;
            }
        }
        finally {
            sessionTracer.endEvent();
        }
        throw new StandardException("Unsupported SQL statement");
    }

    /* PostgresServerSession */

    @Override
    public PostgresMessenger getMessenger() {
        return messenger;
    }

    @Override
    public int getVersion() {
        return version;
    }

    @Override
    public Properties getProperties() {
        return properties;
    }

    @Override
    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    @Override
    public String getProperty(String key, String defval) {
        return properties.getProperty(key, defval);
    }

    @Override
    public Map<String,Object> getAttributes() {
        return attributes;
    }

    @Override
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    @Override
    public void setAttribute(String key, Object attr) {
        attributes.put(key, attr);
        sessionChanged();
    }

    @Override
    public ServiceManager getServiceManager() {
        return serviceManager;
    }

    @Override
    public Session getSession() {
        return session;
    }

    @Override
    public String getDefaultSchemaName() {
        return defaultSchemaName;
    }

    @Override
    public void setDefaultSchemaName(String defaultSchemaName) {
        this.defaultSchemaName = defaultSchemaName;
        sessionChanged();
    }

    @Override
    public AkibanInformationSchema getAIS() {
        return ais;
    }

    @Override
    public SQLParser getParser() {
        return parser;
    }
    
    @Override
    public SessionTracer getSessionTracer() {
        return sessionTracer;
    }
    
    public boolean isInstrumentationEnabled() {
        return instrumentationEnabled;
    }
    
    public void enableInstrumentation() {
        sessionTracer.enable();
        instrumentationEnabled = true;
    }
    
    public void disableInstrumentation() {
        sessionTracer.disable();
        instrumentationEnabled = false;
    }
    
    public String getSqlString() {
        return sql;
    }
    
    public String getRemoteAddress() {
        return socket.getInetAddress().getHostAddress();
    }

}
