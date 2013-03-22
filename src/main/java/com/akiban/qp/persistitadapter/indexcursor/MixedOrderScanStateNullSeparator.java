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

package com.akiban.qp.persistitadapter.indexcursor;


import com.akiban.server.types3.mcompat.mtypes.MNumeric;
import com.persistit.Exchange;
import com.persistit.Key;
import com.persistit.exception.PersistitException;

class MixedOrderScanStateNullSeparator<S,E> extends MixedOrderScanStateSingleSegment<S, E>
{
    @Override
    public boolean jump(S fieldValue) throws PersistitException
    {
        Exchange exchange = cursor.exchange();
        if (!ascending) {
            exchange.append(Key.AFTER);
        }
        boolean resume = exchange.traverse(ascending ? Key.Direction.GTEQ : Key.Direction.LTEQ, true);
        return resume;
    }

    public MixedOrderScanStateNullSeparator(IndexCursorMixedOrder cursor,
                                            int field,
                                            boolean ascending,
                                            SortKeyAdapter<S, E> sortKeyAdapter)
        throws PersistitException
    {
        super(cursor, field, ascending, sortKeyAdapter, MNumeric.BIGINT.instance(false));
    }
}
