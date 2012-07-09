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
import com.akiban.server.types3.TExecutionContext;
import com.akiban.server.types3.TOverload;
import com.akiban.server.types3.TOverloadResult;
import com.akiban.server.types3.pvalue.PValueSource;
import com.akiban.server.types3.pvalue.PValueTarget;
import com.akiban.server.types3.texpressions.TInputSetBuilder;
import com.akiban.server.types3.texpressions.TOverloadBase;

public abstract class Locate extends TOverloadBase
{
    public static TOverload create2ArgOverload(final TClass stringType, final TClass intType, String name)
    {
        return new Locate(stringType, intType, name)
        {
            @Override
            protected void buildInputSets(TInputSetBuilder builder)
            {
                builder.covers(stringType, 0, 1);
            }
        };
    }
    
    public static TOverload create3ArgOverload(final TClass stringType, final TClass intType, String name)
    {
        return new Locate(stringType, intType, name)
        {
            @Override
            protected void buildInputSets(TInputSetBuilder builder)
            {
                builder.covers(stringType, 0, 1).covers(intType, 2);
            }
        };
    }
    
    private final TClass stringType;
    private final TClass intType;
    private final String name;
    
    Locate(TClass stringType, TClass intType, String name)
    {
        this.stringType = stringType;
        this.intType = intType;
        this.name = name;
    }

    @Override
    protected void doEvaluate(TExecutionContext context, LazyList<? extends PValueSource> inputs, PValueTarget output)
    {
        String str = (String) inputs.get(0).getObject();
        String substr = (String)inputs.get(1).getObject();

        int index = 0;
        if (inputs.size() == 3)
        {
            index = inputs.get(2).getInt32() - 1; // mysql uses 1-based indexing
            
            // invalid index => return 0 as the result
            if (index < 0 || index > str.length())
            {
                output.putInt32(0);
                return;
            }
        }
        output.putInt32(1 + str.indexOf(substr, index));
    }

    @Override
    public String overloadName()
    {
        return name;
    }

    @Override
    public TOverloadResult resultType()
    {
        return TOverloadResult.fixed(intType.instance());
    }
}
