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

package com.akiban.server.types3.texpressions;

import com.akiban.server.types3.TAggregator;
import com.akiban.server.types3.TInstance;
import com.akiban.server.types3.pvalue.PValue;
import com.akiban.server.types3.pvalue.PValueSource;
import com.akiban.server.types3.pvalue.PValueTarget;

public final class TValidatedAggregator extends TValidatedOverload implements TAggregator {
    @Override
    public void input(TInstance instance, PValueSource source, TInstance stateType, PValue state, Object option) {
        aggregator.input(instance, source, stateType, state, option);
    }

    @Override
    public void emptyValue(PValueTarget state) {
        aggregator.emptyValue(state);
    }

    public TValidatedAggregator(TAggregator overload) {
        super(overload);
        this.aggregator = overload;
    }
    
    private final TAggregator aggregator;
}
