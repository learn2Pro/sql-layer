package com.akiban.cserver.api.dml.scan;

import com.akiban.cserver.RowData;
import com.akiban.cserver.RowDef;
import com.akiban.cserver.api.common.ColumnId;
import com.akiban.cserver.api.common.TableId;

import java.util.Map;

public final class LegacyRowWrapper implements NewRow {
    private RowData rowData;

    public LegacyRowWrapper() {

    }

    public LegacyRowWrapper(RowData rowData) {
        setRowData(rowData);
    }

    public void setRowData(RowData rowData) {
        this.rowData = rowData;
    }

    @Override
    public Object put(ColumnId index, Object object) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TableId getTableId() {
        return TableId.of(rowData.getRowDefId());
    }

    @Override
    public Object get(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<ColumnId, Object> getFields() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean needsRowDef() {
        return false;
    }

    @Override
    public RowData toRowData(RowDef rowDef) {
        return rowData;
    }
}
