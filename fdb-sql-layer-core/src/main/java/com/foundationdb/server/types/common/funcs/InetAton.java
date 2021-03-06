/**
 * Copyright (C) 2009-2013 FoundationDB, LLC
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

package com.foundationdb.server.types.common.funcs;

import com.foundationdb.server.error.InvalidCharToNumException;
import com.foundationdb.server.types.LazyList;
import com.foundationdb.server.types.TClass;
import com.foundationdb.server.types.TExecutionContext;
import com.foundationdb.server.types.TOverloadResult;
import com.foundationdb.server.types.common.types.TString;
import com.foundationdb.server.types.value.ValueSource;
import com.foundationdb.server.types.value.ValueTarget;
import com.foundationdb.server.types.texpressions.TInputSetBuilder;
import com.foundationdb.server.types.texpressions.TScalarBase;

public class InetAton extends TScalarBase
{
    private static final long FACTORS[] = {16777216L,  65536, 256};        
    
    private final TClass argType;
    private final TClass returnType;
    
    public InetAton(TClass tclass, TClass returnType)
    {
        assert tclass instanceof TString : "expecting a string class";
        this.argType = tclass;
        
        // TODO: assert returnType instaceof BIGINT ...
        this.returnType = returnType;
    }

    @Override
    protected void buildInputSets(TInputSetBuilder builder)
    {
        builder.covers(argType, 0);
    }

    @Override
    protected void doEvaluate(TExecutionContext context, LazyList<? extends ValueSource> inputs, ValueTarget output)
    {
        String tks[] = (inputs.get(0).getString()).split("\\.");
        if (tks.length > 4)
            output.putNull();
        else
            try
            {
                int last = tks.length - 1;
                short val = Short.parseShort(tks[last]);
                long ret = val;
                
                if (ret < 0 || ret > 255) output.putNull();
                else if (tks.length == 1) output.putInt64(ret);
                else
                {
                    for (int i = 0; i < last; ++i)
                        if ((val = Short.parseShort(tks[i])) < 0 || val > 255)
                        {
                            output.putNull();
                            return;
                        }
                        else
                            ret += val * FACTORS[i];
                    output.putInt64(ret);
                }
            }
            catch (NumberFormatException e)
            {
                context.warnClient(new InvalidCharToNumException(e.getMessage()));
                output.putNull();
            }
    }

    @Override
    public String displayName()
    {
        return "INET_ATON";
    }

    @Override
    public TOverloadResult resultType()
    {
        return TOverloadResult.fixed(returnType);
    }
}
