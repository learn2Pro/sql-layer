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

package com.akiban.qp.persistitadapter;

import com.akiban.qp.operator.API;
import com.akiban.qp.operator.Cursor;
import com.akiban.qp.operator.CursorLifecycle;
import com.akiban.qp.operator.QueryContext;
import com.akiban.qp.persistitadapter.indexcursor.Sorter;
import com.akiban.qp.row.Row;
import com.akiban.qp.rowtype.RowType;
import com.akiban.server.api.dml.ColumnSelector;
import com.akiban.util.tap.InOutTap;
import com.persistit.exception.PersistitException;

// Cursors are reusable but Sorters are not. This class creates a new Sorter each time a new cursor scan is started.

class SorterToCursorAdapter implements Cursor
{
    // Cursor interface

    @Override
    public void open()
    {
        CursorLifecycle.checkIdle(this);
        try {
            sorter = new Sorter(context, input, rowType, ordering, sortOption, loadTap, usePValues);
            cursor = sorter.sort();
            cursor.open();
        } catch (PersistitException e) {
            adapter.handlePersistitException(e);
        }
    }

    @Override
    public Row next()
    {
        CursorLifecycle.checkIdleOrActive(this);
        return cursor == null ? null : cursor.next();
    }

    @Override
    public void jump(Row row, ColumnSelector columnSelector)
    {
        throw new UnsupportedOperationException(getClass().getName());
    }

    @Override
    public void close()
    {
        CursorLifecycle.checkIdleOrActive(this);
        if (cursor != null) {
            cursor.close();
            // Destroy here because Sorters can only be used once.
            cursor.destroy();
            cursor = null;
        }
        if (sorter != null) {
            sorter.close();
            sorter = null;
        }
    }

    @Override
    public void destroy()
    {
        close();
        destroyed = true;
    }

    @Override
    public boolean isIdle()
    {
        return !destroyed && cursor == null;
    }

    @Override
    public boolean isActive()
    {
        return !destroyed && cursor != null;
    }

    @Override
    public boolean isDestroyed()
    {
        return destroyed;
    }

    // SorterToCursorAdapter interface

    public SorterToCursorAdapter(PersistitAdapter adapter,
                                 QueryContext context,
                                 Cursor input,
                                 RowType rowType,
                                 API.Ordering ordering,
                                 API.SortOption sortOption,
                                 InOutTap loadTap,
                                 boolean usePValues)
    {
        this.adapter = adapter;
        this.context = context;
        this.input = input;
        this.rowType = rowType;
        this.ordering = ordering;
        this.sortOption = sortOption;
        this.loadTap = loadTap;
        this.usePValues = usePValues;
    }

    private final PersistitAdapter adapter;
    private final QueryContext context;
    private final Cursor input;
    private final RowType rowType;
    private final API.Ordering ordering;
    private final API.SortOption sortOption;
    private final InOutTap loadTap;
    private final boolean usePValues;
    private Sorter sorter;
    private Cursor cursor;
    private boolean destroyed = false;
}
