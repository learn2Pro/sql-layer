package com.akiban.cserver.api.common;

import com.akiban.ais.model.TableName;
import com.akiban.cserver.RowDef;
import com.akiban.cserver.api.dml.NoSuchTableException;

public interface IdResolver {
    int tableId(TableName tableName) throws NoSuchTableException;

    TableName tableName(int id) throws NoSuchTableException;

    RowDef getRowDef(TableId id) throws NoSuchTableException;
}
