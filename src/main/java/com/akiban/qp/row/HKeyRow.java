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

package com.akiban.qp.row;

import com.akiban.ais.model.UserTable;
import com.akiban.qp.rowtype.HKeyRowType;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.util.HKeyCache;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types3.pvalue.PValueSource;

public class HKeyRow extends AbstractRow
{
    // Object interface

    @Override
    public String toString()
    {
        return hKey.toString();
    }

    // Row interface

    @Override
    public RowType rowType()
    {
        return rowType;
    }

    @Override
    public ValueSource eval(int i)
    {
        return hKey.eval(i);
    }

    @Override
    public PValueSource pvalue(int i) {
        return hKey.pEval(i);
    }

    @Override
    public HKey hKey()
    {
        return hKey;
    }

    @Override
    public HKey ancestorHKey(UserTable table)
    {
        // TODO: This does the wrong thing for hkeys derived from group index rows!
        // TODO: See bug 997746.
        HKey ancestorHKey = hKeyCache.hKey(table);
        hKey.copyTo(ancestorHKey);
        ancestorHKey.useSegments(table.getDepth() + 1);
        return ancestorHKey;
    }

    @Override
    public Row subRow(RowType subRowType)
    {
        throw new UnsupportedOperationException();
    }

    // HKeyRow interface

    public HKeyRow(HKeyRowType rowType, HKey hKey, HKeyCache<HKey> hKeyCache)
    {
        this.hKeyCache = hKeyCache;
        this.rowType = rowType;
        this.hKey = hKey;
    }
    
    // Object state

    private final HKeyCache<HKey> hKeyCache;
    private final HKeyRowType rowType;
    private HKey hKey;
}
