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

package com.foundationdb.ais.model;

import com.foundationdb.server.types.AkType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Definitive declaration of supported data types. The fields in each Type
 * instance are:
 * 
 * <dl>
 * <dt>name</dt>
 * <dd>Canonical name for the type</dd>
 * 
 * <dt>nparams</dt>
 * <dd>How many parameters are specified by DDL (0, 1 or 2)</dd>
 * 
 * <dt>fixed</dt>
 * <dd>Whether the storage size is fixed, e.g., true for int, false for
 * varchar</dt>
 * 
 * <dt>maxBytesize</dt>
 * <dd>Storage size of elements. For fixed types, the chunk server relies on
 * this to determine how to encode/decode values. For variable-length fields,
 * this is the maximum number of bytes of data MySQL may encode, it DOES NOT
 * include an allowance for the prefix bytes written by MySQL.
 * 
 * <dt>encoding</dt>
 * <dd>Name of a member of the chunk server's Encoding enum. This guides
 * translation of a column value into a chunk server's internal format.</dd>
 * </dl>
 * 
 * @author peter
 * 
 */
public class Types {
    
    // TODO -
    // This is the largest BLOB size that will fit in a message.  Increase or
    // remove this when we are no longer limited by the message size.
    // Note that the Type objects for the BLOB types carry their MySQL-defined
    // values so that the prefix size will be computed correctly.  The
    // cap is imposed by the constructor of a Column object.
    //
    public final static int MAX_STORAGE_SIZE_CAP = 1024 * 1024 - 1024;

	// The basic numeric types, fixed length, implemented
	// (except bigint unsigned fails for numbers larger than Long.MAX_VALUE).
	//
    public static final Type BIGINT =       new Type("bigint", 0, true, 8L, "INT", AkType.LONG);
    public static final Type U_BIGINT = 	  new Type("bigint unsigned", 0, true, 8L, "U_BIGINT", AkType.U_BIGINT);
    public static final Type DOUBLE =       new Type("double", 0, true, 8L, "DOUBLE", AkType.DOUBLE);
    public static final Type U_DOUBLE =     new Type("double unsigned", 0, true, 8L, "U_DOUBLE", AkType.U_DOUBLE);
    public static final Type FLOAT =        new Type("float", 0, true, 4L, "FLOAT", AkType.FLOAT);
    public static final Type U_FLOAT =      new Type("float unsigned", 0, true, 4L, "U_FLOAT", AkType.U_FLOAT);
    public static final Type INT =          new Type("int", 0, true, 4L, "INT", AkType.INT);
    public static final Type U_INT =        new Type("int unsigned", 0, true, 4L, "U_INT", AkType.U_INT);
    public static final Type MEDIUMINT =    new Type("mediumint", 0, true, 3L, "INT", AkType.INT);
    public static final Type U_MEDIUMINT =  new Type("mediumint unsigned", 0, true, 3L, "U_INT", AkType.U_INT);
    public static final Type SMALLINT =     new Type("smallint", 0, true, 2L, "INT", AkType.INT);
    public static final Type U_SMALLINT =   new Type("smallint unsigned", 0, true, 2L, "U_INT", AkType.U_INT);
    public static final Type TINYINT =      new Type("tinyint", 0, true, 1L, "INT", AkType.INT);
    public static final Type U_TINYINT =    new Type("tinyint unsigned", 0, true, 1L, "U_INT", AkType.U_INT);
	//
	// Date & Time types, fixed length, implemented.
	//
    public static final Type DATE =         new Type("date", 0, true, 3L, "DATE", AkType.DATE);
    public static final Type DATETIME =     new Type("datetime", 0, true, 8L,	"DATETIME", AkType.DATETIME);
    public static final Type YEAR =         new Type("year", 0, true, 1L, "YEAR", AkType.YEAR);
    public static final Type TIME =         new Type("time", 0, true, 3L, "TIME", AkType.TIME);
    public static final Type TIMESTAMP =    new Type("timestamp", 0, true, 4L, "TIMESTAMP", AkType.TIMESTAMP);
        //
    // VARCHAR and TEXT types. Maximum storage size is computed in Column, numbers
    // here are not used. MaxByteSize numbers here are not used.
    //
    public static final Type VARBINARY =    new Type("varbinary", 1, false, 65535L, "VARBINARY", AkType.VARBINARY);
    public static final Type BINARY =       new Type("binary", 1, false, 255L, "VARBINARY", AkType.VARBINARY);
    public static final Type VARCHAR =      new Type("varchar", 1, false, 65535L, "VARCHAR", AkType.VARCHAR);
    public static final Type CHAR =         new Type("char", 1, false, 767L, "VARCHAR", AkType.VARCHAR);
        //
	// BLOB and TEXT types.  Currently handled identically. The maxByteSize values
	// here are used in computing the correct prefix size.  The maximum allow size
	// is constrained in Column.
	//
    public static final Type TINYBLOB =     new Type("tinyblob", 0, false, 0xFFl, "BLOB", AkType.TEXT);
    public static final Type TINYTEXT =     new Type("tinytext", 0, false, 0xFFl, "TEXT", AkType.TEXT);
    public static final Type BLOB =         new Type("blob", 0, false, 0xFFFFl, "BLOB", AkType.TEXT);
    public static final Type TEXT =         new Type("text", 0, false, 0xFFFFl, "TEXT", AkType.TEXT);
    public static final Type MEDIUMBLOB =   new Type("mediumblob", 0, false, 0xFFFFFFL, "BLOB", AkType.TEXT);
    public static final Type MEDIUMTEXT =   new Type("mediumtext", 0, false, 0xFFFFFFL, "TEXT", AkType.TEXT);
    public static final Type LONGBLOB =     new Type("longblob", 0, false, 0xFFFFFFFFL, "BLOB", AkType.TEXT);
    public static final Type LONGTEXT =     new Type("longtext", 0, false, 0xFFFFFFFFL, "TEXT", AkType.TEXT);
    //
	// DECIMAL types. The maxByteSize values are computed in Column as they are fixed for
	// a given instance. Numbers are a maximum possible (ie, decimal(65,30));
	//
    public static final Type DECIMAL =      new Type("decimal", 2, true, 30L, "DECIMAL", AkType.DECIMAL);
    public static final Type U_DECIMAL =    new Type("decimal unsigned", 2, true, 30L, "U_DECIMAL", AkType.DECIMAL);
	//
	// Halo unsupported
	//
    public static final Type ENUM =         new Type("enum", 1, true, 2L, "U_INT", AkType.U_INT);
    public static final Type SET =          new Type("set", 1, true, 8L, "U_INT", AkType.U_INT);
    public static final Type BIT =          new Type("bit", 1, true, 9L, "BIT", AkType.UNSUPPORTED);
    public static final Type GEOMETRY =           new Type("geometry", 0, false, 0L, "GEOMETRY", AkType.UNSUPPORTED);
    public static final Type GEOMETRYCOLLECTION = new Type("geometrycollection", 0, false, 0L, "GEOMETRYCOLLECTION", AkType.UNSUPPORTED);
    public static final Type POINT =              new Type("point", 0, false, 0L, "POINT", AkType.UNSUPPORTED);
    public static final Type MULTIPOINT =         new Type("multipoint", 0, false, 0L, "MULTIPOINT", AkType.UNSUPPORTED);
    public static final Type LINESTRING =         new Type("linestring", 0, false, 0L, "LINESTRING", AkType.UNSUPPORTED);
    public static final Type MULTILINESTRING =    new Type("multilinestring", 0, false, 0L, "MULTILINESTRING", AkType.UNSUPPORTED);
    public static final Type POLYGON =            new Type("polygon", 0, false, 0L, "POLYGON", AkType.UNSUPPORTED);
    public static final Type MULTIPOLYGON =       new Type("multipolygon", 0, false, 0L, "MULTIPOLYGON", AkType.UNSUPPORTED);

    public static final Type BOOLEAN = new Type("boolean", 0, true, 1L, "BOOLEAN", AkType.BOOL);

    private final static List<Type> types = listOfTypes();
    private final static Set<Type> unsupported = setOfUnsupportedTypes();
    private final static Set<Type> unsupportedInIndex = setOfUnsupportedIndexTypes();
    private final static Map<Type,Long[]> defaultParams = mapOfDefaults();
    private final static Map<Type,TypesEnum> asEnums = mapToEnumTypes();

    private static List<Type> listOfTypes() {
        List<Type> types = new ArrayList<>();
        types.add(BIGINT);
        types.add(U_BIGINT);
        types.add(BINARY);
        types.add(BIT);
        types.add(BLOB);
        types.add(CHAR);
        types.add(DATE);
        types.add(DATETIME);
        types.add(DECIMAL);
        types.add(U_DECIMAL);
        types.add(DOUBLE);
        types.add(U_DOUBLE);
        types.add(ENUM);
        types.add(FLOAT);
        types.add(U_FLOAT);
        types.add(GEOMETRY);
        types.add(GEOMETRYCOLLECTION);
        types.add(INT);
        types.add(U_INT);
        types.add(LINESTRING);
        types.add(LONGBLOB);
        types.add(LONGTEXT);
        types.add(MEDIUMBLOB);
        types.add(MEDIUMINT);
        types.add(U_MEDIUMINT);
        types.add(MEDIUMTEXT);
        types.add(MULTILINESTRING);
        types.add(MULTIPOINT);
        types.add(MULTIPOLYGON);
        types.add(POINT);
        types.add(POLYGON);
        types.add(SET);
        types.add(SMALLINT);
        types.add(U_SMALLINT);
        types.add(TEXT);
        types.add(TIME);
        types.add(TIMESTAMP);
        types.add(TINYBLOB);
        types.add(TINYINT);
        types.add(U_TINYINT);
        types.add(TINYTEXT);
        types.add(VARBINARY);
        types.add(VARCHAR);
        types.add(YEAR);
        types.add(BOOLEAN);
        return Collections.unmodifiableList(types);
	}

    private static Map<Type,TypesEnum> mapToEnumTypes() {
        Map<Type,TypesEnum> result = new HashMap<>(TypesEnum.values().length);
        for (TypesEnum asEnum : TypesEnum.values()) {
            String fieldName = asEnum.name().substring(2);
            try {
                Field field = Types.class.getField(fieldName);
                Type asType = (Type) field.get(null);
                result.put(asType, asEnum);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public enum TypesEnum {
        T_BIGINT,
        T_U_BIGINT,
        T_BINARY,
        T_BIT,
        T_BLOB,
        T_CHAR,
        T_DATE,
        T_DATETIME,
        T_DECIMAL,
        T_U_DECIMAL,
        T_DOUBLE,
        T_U_DOUBLE,
        T_ENUM,
        T_FLOAT,
        T_U_FLOAT,
        T_GEOMETRY,
        T_GEOMETRYCOLLECTION,
        T_INT,
        T_U_INT,
        T_LINESTRING,
        T_LONGBLOB,
        T_LONGTEXT,
        T_MEDIUMBLOB,
        T_MEDIUMINT,
        T_U_MEDIUMINT,
        T_MEDIUMTEXT,
        T_MULTILINESTRING,
        T_MULTIPOINT,
        T_MULTIPOLYGON,
        T_POINT,
        T_POLYGON,
        T_SET,
        T_SMALLINT,
        T_U_SMALLINT,
        T_TEXT,
        T_TIME,
        T_TIMESTAMP,
        T_TINYBLOB,
        T_TINYINT,
        T_U_TINYINT,
        T_TINYTEXT,
        T_VARBINARY,
        T_VARCHAR,
        T_YEAR,
        T_BOOLEAN
    }

    private static Set<Type> setOfUnsupportedTypes() {
        Set<Type> unsupported = new HashSet<>();
        unsupported.add(null);
        unsupported.add(BIT);
        unsupported.add(ENUM);
        unsupported.add(SET);
        unsupported.add(GEOMETRY);
        unsupported.add(GEOMETRYCOLLECTION);
        unsupported.add(POINT);
        unsupported.add(MULTIPOINT);
        unsupported.add(LINESTRING);
        unsupported.add(MULTILINESTRING);
        unsupported.add(POLYGON);
        unsupported.add(MULTIPOLYGON);
        return Collections.unmodifiableSet(unsupported);
    }

    private static Set<Type> setOfUnsupportedIndexTypes() {
        Set<Type> unsupported = new HashSet<>();
        unsupported.add(TINYBLOB);
        unsupported.add(BLOB);
        unsupported.add(MEDIUMBLOB);
        unsupported.add(LONGBLOB);
        unsupported.add(TINYTEXT);
        unsupported.add(TEXT);
        unsupported.add(MEDIUMTEXT);
        unsupported.add(LONGTEXT);
        return Collections.unmodifiableSet(unsupported);
    }

    private static Map<Type,Long[]> mapOfDefaults() {
        Map<Type,Long[]> map = new HashMap<>();
        map.put(BIT, new Long[]{1L,null});
        map.put(BINARY, new Long[]{1L,null});
        map.put(CHAR, new Long[]{1L,null});
        map.put(DECIMAL, new Long[]{10L,0L});
        map.put(U_DECIMAL, new Long[]{10L,0L});
        return Collections.unmodifiableMap(map);
    }

    /**
     * Get the type's enum, for using in switches.
     */
    public static TypesEnum asEnum(Type type) {
        TypesEnum result = asEnums.get(type);
        if (result == null)
            throw new IllegalArgumentException("no enum value for " + type);
        return result;
    }

    /**
     * List of all known types.
     */
    public static List<Type> types() {
        return types;
    }

    /**
     * Set of all known types that are unsupported.
     */
    public static Set<Type> unsupportedTypes() {
        return unsupported;
    }

    /**
     * Set of all <b>supported</b> types that cannot be used in an index.
     */
    public static Set<Type> unsupportedIndexTypes() {
        return unsupportedInIndex;
    }

    public static Map<Type,Long[]> defaultParams() {
        return defaultParams;
    }

    public static boolean isTextType(Type type) {
        return type.equals(CHAR) || type.equals(VARCHAR) || type.equals(TEXT);
    }

    public static boolean isIntType(Type type) {
        return type.equals(TINYINT) || type.equals(U_TINYINT) ||
               type.equals(SMALLINT) || type.equals(U_SMALLINT) ||
               type.equals(INT) || type.equals(U_INT) ||
               type.equals(MEDIUMINT) || type.equals(U_MEDIUMINT) ||
               type.equals(BIGINT);
    }
}