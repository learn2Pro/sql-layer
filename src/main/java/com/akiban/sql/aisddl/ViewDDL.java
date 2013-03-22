/**
 * Copyright (C) 2009-2013 Akiban Technologies, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.akiban.sql.aisddl;

import com.akiban.server.api.DDLFunctions;
import com.akiban.server.error.*;
import com.akiban.server.service.session.Session;

import com.akiban.sql.optimizer.AISBinderContext;
import com.akiban.sql.optimizer.AISViewDefinition;
import com.akiban.sql.parser.CreateViewNode;
import com.akiban.sql.parser.DropViewNode;
import com.akiban.sql.parser.ExistenceCheck;
import com.akiban.sql.parser.NodeTypes;
import com.akiban.sql.parser.ResultColumn;
import com.akiban.sql.types.DataTypeDescriptor;
import com.akiban.sql.types.TypeId;

import com.akiban.ais.model.AISBuilder;
import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.Columnar;
import com.akiban.ais.model.TableName;
import com.akiban.ais.model.View;

import com.akiban.qp.operator.QueryContext;
import java.util.Collection;
import java.util.Map;

/** DDL operations on Views */
public class ViewDDL
{
    private ViewDDL() {
    }

    public static void createView(DDLFunctions ddlFunctions,
                                  Session session,
                                  String defaultSchemaName,
                                  CreateViewNode createView,
                                  AISBinderContext binderContext,
                                  QueryContext context) {
        com.akiban.sql.parser.TableName parserName = createView.getObjectName();
        String schemaName = parserName.hasSchema() ? parserName.getSchemaName() : defaultSchemaName;
        String viewName = parserName.getTableName();
        ExistenceCheck condition = createView.getExistenceCheck();

        if (ddlFunctions.getAIS(session).getView(schemaName, viewName) != null) {
            switch(condition) {
            case IF_NOT_EXISTS:
                // view already exists. does nothing
                if (context != null)
                    context.warnClient(new DuplicateViewException(schemaName, viewName));
                return;
            case NO_CONDITION:
                throw new DuplicateViewException(schemaName, viewName);
            default:
                throw new IllegalStateException("Unexpected condition: " + condition);
            }
        }
        
        AISViewDefinition viewdef = binderContext.getViewDefinition(createView);
        Map<TableName,Collection<String>> tableColumnReferences = viewdef.getTableColumnReferences();
        AISBuilder builder = new AISBuilder();
        builder.view(schemaName, viewName, viewdef.getQueryExpression(), 
                     binderContext.getParserProperties(schemaName), tableColumnReferences);
        int colpos = 0;
        for (ResultColumn rc : viewdef.getResultColumns()) {
            DataTypeDescriptor type = rc.getType();
            if (type == null) {
                if (rc.getExpression().getNodeType() != NodeTypes.UNTYPED_NULL_CONSTANT_NODE)
                    throw new AkibanInternalException(rc.getName() + " has unknown type");
                type = new DataTypeDescriptor(TypeId.CHAR_ID, true, 0);
            }
            TableDDL.addColumn(builder, schemaName, viewName, rc.getName(), colpos++,
                               type, type.isNullable(), false, null);
        }
        View view = builder.akibanInformationSchema().getView(schemaName, viewName);
        ddlFunctions.createView(session, view);
    }

    public static void dropView (DDLFunctions ddlFunctions,
                                 Session session, 
                                 String defaultSchemaName,
                                 DropViewNode dropView,
                                 AISBinderContext binderContext,
                                 QueryContext context) {
        com.akiban.sql.parser.TableName parserName = dropView.getObjectName();
        String schemaName = parserName.hasSchema() ? parserName.getSchemaName() : defaultSchemaName;
        TableName viewName = TableName.create(schemaName, parserName.getTableName());
        ExistenceCheck existenceCheck = dropView.getExistenceCheck();

        if (ddlFunctions.getAIS(session).getView(viewName) == null) {
            if (existenceCheck == ExistenceCheck.IF_EXISTS)
            {
                if (context != null)
                    context.warnClient(new UndefinedViewException(viewName));
                return;
            }
            throw new UndefinedViewException(viewName);
        }
        checkDropTable(ddlFunctions, session, viewName);
        ddlFunctions.dropView(session, viewName);
    }

    public static void checkDropTable(DDLFunctions ddlFunctions, Session session, 
                                      TableName name) {
        AkibanInformationSchema ais = ddlFunctions.getAIS(session);
        Columnar table = ais.getColumnar(name);
        if (table == null) return;
        for (View view : ais.getViews().values()) {
            if (view.referencesTable(table)) {
                throw new ViewReferencesExist(view.getName().getSchemaName(),
                                              view.getName().getTableName(),
                                              table.getName().getSchemaName(),
                                              table.getName().getTableName());
            }
        }
    }

}
