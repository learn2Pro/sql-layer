/**
 * END USER LICENSE AGREEMENT (“EULA”)
 *
 * READ THIS AGREEMENT CAREFULLY (date: 9/13/2011):
 * http://www.akiban.com/licensing/20110913
 *
 * BY INSTALLING OR USING ALL OR ANY PORTION OF THE SOFTWARE, YOU ARE ACCEPTING
 * ALL OF THE TERMS AND CONDITIONS OF THIS AGREEMENT. YOU AGREE THAT THIS
 * AGREEMENT IS ENFORCEABLE LIKE ANY WRITTEN AGREEMENT SIGNED BY YOU.
 *
 * IF YOU HAVE PAID A LICENSE FEE FOR USE OF THE SOFTWARE AND DO NOT AGREE TO
 * THESE TERMS, YOU MAY RETURN THE SOFTWARE FOR A FULL REFUND PROVIDED YOU (A) DO
 * NOT USE THE SOFTWARE AND (B) RETURN THE SOFTWARE WITHIN THIRTY (30) DAYS OF
 * YOUR INITIAL PURCHASE.
 *
 * IF YOU WISH TO USE THE SOFTWARE AS AN EMPLOYEE, CONTRACTOR, OR AGENT OF A
 * CORPORATION, PARTNERSHIP OR SIMILAR ENTITY, THEN YOU MUST BE AUTHORIZED TO SIGN
 * FOR AND BIND THE ENTITY IN ORDER TO ACCEPT THE TERMS OF THIS AGREEMENT. THE
 * LICENSES GRANTED UNDER THIS AGREEMENT ARE EXPRESSLY CONDITIONED UPON ACCEPTANCE
 * BY SUCH AUTHORIZED PERSONNEL.
 *
 * IF YOU HAVE ENTERED INTO A SEPARATE WRITTEN LICENSE AGREEMENT WITH AKIBAN FOR
 * USE OF THE SOFTWARE, THE TERMS AND CONDITIONS OF SUCH OTHER AGREEMENT SHALL
 * PREVAIL OVER ANY CONFLICTING TERMS OR CONDITIONS IN THIS AGREEMENT.
 */

package com.akiban.server.types3.common.funcs;

import com.akiban.server.types3.LazyList;
import com.akiban.server.types3.TClass;
import com.akiban.server.types3.TCustomOverloadResult;
import com.akiban.server.types3.TExecutionContext;
import com.akiban.server.types3.TInstance;
import com.akiban.server.types3.TOverload;
import com.akiban.server.types3.TOverloadResult;
import com.akiban.server.types3.TPreptimeContext;
import com.akiban.server.types3.TPreptimeValue;
import com.akiban.server.types3.pvalue.PValueSource;
import com.akiban.server.types3.pvalue.PValueTarget;
import com.akiban.server.types3.texpressions.TInputSetBuilder;
import com.akiban.server.types3.texpressions.TOverloadBase;
import java.util.List;

public abstract class UpperLower extends TOverloadBase
{
    public static TOverload[] create(TClass stringType)
    {
        return new TOverload[]
        {
            new UpperLower(stringType, "UPPER")
            {
                @Override
                protected void doEvaluate(TExecutionContext context, LazyList<? extends PValueSource> inputs, PValueTarget output)
                {
                    output.putObject((inputs.get(0).getString()).toUpperCase());
                }
                
                @Override
                public String[] registeredNames()
                {
                    return new String[]{"lcase", "lower"};
                }
            },
            new UpperLower(stringType, "LOWER")
            {
                @Override
                protected void doEvaluate(TExecutionContext context, LazyList<? extends PValueSource> inputs, PValueTarget output)
                {
                    output.putObject((inputs.get(0).getString()).toLowerCase());
                }
                
                @Override
                public String[] registeredNames()
                {
                    return new String[]{"ucase", "upper"};
                }
            }
        };
    }

    private final TClass stringType;
    private final String name;
    private UpperLower(TClass stringType, String name)
    {
        this.stringType = stringType;
        this.name = name;
    }
    
    @Override
    protected void buildInputSets(TInputSetBuilder builder)
    {
        builder.covers(stringType, 0);
    }

    @Override
    public String displayName()
    {
        return name;
    }

    @Override
    public TOverloadResult resultType()
    {
        // actual return type is exactly the same as input type
        return TOverloadResult.custom(new TCustomOverloadResult()
        {
            @Override
            public TInstance resultInstance(List<TPreptimeValue> inputs, TPreptimeContext context)
            {
                return inputs.get(0).instance();
            }
        });
    }
}
