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

import com.akiban.qp.expression.BoundExpressions;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.util.ValueHolder;
import com.akiban.server.types3.Types3Switch;
import com.akiban.server.types3.pvalue.PValue;
import com.akiban.server.types3.pvalue.PValueSource;

class SpatialIndexBoundExpressions implements BoundExpressions
{
    // BoundExpressions interface

    @Override
    public PValueSource pvalue(int position)
    {
        return pValueSources[position];
    }

    @Override
    public ValueSource eval(int position)
    {
        return valueSources[position];
    }

    // SpatialIndexBoundExpressions interface

    public void value(int position, PValueSource valueSource)
    {
        pValueSources[position] = valueSource;
    }

    public void value(int position, ValueSource valueSource)
    {
        valueSources[position] = valueSource;
    }

    public SpatialIndexBoundExpressions(int nFields)
    {
        if (Types3Switch.ON) {
            pValueSources = new PValue[nFields];
        } else {
            valueSources = new ValueHolder[nFields];
        }
    }

    // Object state

    private PValueSource[] pValueSources;
    private ValueSource[] valueSources;
}
