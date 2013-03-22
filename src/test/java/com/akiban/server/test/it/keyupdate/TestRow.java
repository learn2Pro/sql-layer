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

package com.akiban.server.test.it.keyupdate;

import com.akiban.server.api.dml.scan.NiceRow;
import com.akiban.server.rowdata.RowDef;
import com.akiban.server.service.session.Session;
import com.akiban.server.store.Store;

public class TestRow extends NiceRow
{
    public TestRow(int tableId, RowDef rowDef, Store store)
    {
        super(tableId, rowDef);
        this.store = store;
    }

    public HKey hKey()
    {
        return hKey;
    }

    public void hKey(HKey hKey)
    {
        this.hKey = hKey;
    }

    public TestRow parent()
    {
        return parent;
    }

    public void parent(TestRow parent)
    {
        this.parent = parent;
    }

    public Store getStore() {
        return store;
    }

    private HKey hKey;
    private TestRow parent;
    private final Store store;
}
