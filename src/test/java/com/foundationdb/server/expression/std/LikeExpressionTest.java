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

package com.foundationdb.server.expression.std;

import com.foundationdb.server.types.ValueSourceIsNullException;
import com.foundationdb.junit.OnlyIf;
import com.foundationdb.junit.OnlyIfNot;
import com.foundationdb.junit.NamedParameterizedRunner;
import com.foundationdb.junit.Parameterization;
import com.foundationdb.junit.ParameterizationBuilder;
import com.foundationdb.server.expression.Expression;
import com.foundationdb.server.expression.ExpressionComposer;
import com.foundationdb.server.types.AkType;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

@RunWith(NamedParameterizedRunner.class)
public class LikeExpressionTest extends ComposedExpressionTestBase
{
    private static final CompositionTestInfo info = new CompositionTestInfo(2, AkType.VARCHAR, true);
    private ExpressionComposer ex;
    private String left;
    private String right;
    private String esc;
    private boolean expected;
    private boolean expectNull;
    private boolean invalidExc;
    protected static boolean already = false;
    public LikeExpressionTest (ExpressionComposer ex, String left, String right, String esc, boolean expected, boolean expectNull, boolean expectExc)
    {
        this.ex = ex;
        this.left = left;
        this.right = right;
        this.esc = esc;
        this.expected = expected;
        this.expectNull = expectNull;
        this.invalidExc = expectExc;
    }

    @NamedParameterizedRunner.TestParameters
    public static Collection<Parameterization> params()
    {
        ParameterizationBuilder pb = new ParameterizationBuilder();
        
        // test tricky case
        param(pb, LikeExpression.LIKE_COMPOSER, "xbz123babcabcab", "%babcabcab%", "\\", true, false, false);
        
        // test with periodic patterns (where you can kind-of 'connect' the head to the tail and make it a circular queue
        param(pb, LikeExpression.LIKE_COMPOSER, "abxabcabcab", "%abcabcab%", "\\", true, false, false);
        param(pb, LikeExpression.LIKE_COMPOSER, "abcabxabcabc", "%abcabc%", "\\", true, false, false);
        
        param(pb, LikeExpression.LIKE_COMPOSER, "zcbacba", "%acba%", "\\", true, false, false);
        
        // test empty/illegal escaped char
        // select 'ab' like '%ab\\%'
        // escape char at end-of-string . => returns NULL + warning 
        param(pb, LikeExpression.LIKE_COMPOSER, "ab", "%ab\\", "\\", false, true, false);
        
        // select 'ab' like '\\'
        // string contains ONLY the escape character => return NULL + warning
        param(pb, LikeExpression.LIKE_COMPOSER, "ab", "\\", "\\", false, true, false);
        
        // select 'abc' like 'abc\\'
        // this returns FALSE as opposed to a NULL
        param(pb, LikeExpression.LIKE_COMPOSER, "abc", "abc\\", "\\", false, false, false);
        
        // select 'ab' like '%\\'
        param(pb, LikeExpression.LIKE_COMPOSER, "ab", "%\\", "\\", false, true, false);
        
        
        //select '-A-abx' like '%-Ab%';
        param(pb, LikeExpression.ILIKE_COMPOSER, "-A-abx", "%-Ab%", "\\", true, false, false);
        
        //'xxxA-AAbx' LIKE '%-AAb%' (duplicate characters in pattern)
        param(pb, LikeExpression.LIKE_COMPOSER, "xxxA-AAbx", "%-AAb%", "\\", true, false, false);
        param(pb, LikeExpression.LIKE_COMPOSER, "xxxA-AAbx-ABC", "%-Ax_%", "\\", false, false, false);

        
        // '-bb-Abx' LIKE '%-Ab%' 
        param(pb, LikeExpression.LIKE_COMPOSER, "-bb-Abx", "%-Ab%", "\\", true, false, false);
        param(pb, LikeExpression.LIKE_COMPOSER, "-bb-bbb-abx", "-bb%-ab%", "\\", true, false, false);
        
        param(pb, LikeExpression.ILIKE_COMPOSER, "ab123b124", "a%%%%%%b%", "\\", true, false, false);
        param(pb, LikeExpression.BLIKE_COMPOSER, "ab123b124", "a%%%%%%%", "\\", true, false, false);
        param(pb, LikeExpression.BLIKE_COMPOSER, "ab123b124", "a%%%%%%%4", "\\", true, false, false);
        param(pb, LikeExpression.LIKE_COMPOSER, "-A-Abx", "%-Ab%", "\\", true, false, false);
             
        param(pb, LikeExpression.BLIKE_COMPOSER, "aX", "axx", "x", false, false, false);
        param(pb, LikeExpression.ILIKE_COMPOSER, "aX", "axx", "x", true, false, false);
        
        param(pb, LikeExpression.ILIKE_COMPOSER, "a%", "ax%", "x", true, false, false);
        param(pb, LikeExpression.ILIKE_COMPOSER, "xyz", "XYZ%", "\\", true, false, false);
        param(pb, LikeExpression.BLIKE_COMPOSER, "xyz", "XYZ%", "\\", false, false, false);

        param(pb, LikeExpression.ILIKE_COMPOSER, "John Smith", "j%h", "\\", true, false, false);
        param(pb, LikeExpression.BLIKE_COMPOSER, "John Smith", "j%h", "\\",false, false, false);

        param(pb, LikeExpression.ILIKE_COMPOSER, "abc_", "%=_", "=", true, false, false);
        param(pb, LikeExpression.BLIKE_COMPOSER, "abc_", "%=_", "=", true, false, false);

        param(pb, LikeExpression.ILIKE_COMPOSER, "_abc", "_%", "=",true, false, false);
        param(pb, LikeExpression.BLIKE_COMPOSER, "_abc", "_%", "\\",true, false, false);

        param(pb, LikeExpression.ILIKE_COMPOSER, "ab", "a=%",  "=",false, false, false);
        param(pb, LikeExpression.BLIKE_COMPOSER, "ab", "a=%", "=",false, false, false);

        param(pb, LikeExpression.ILIKE_COMPOSER, "ab", "a=_", "=", false, false, false);
        param(pb, LikeExpression.BLIKE_COMPOSER, "ab", "a=_", "=",false, false, false);

        param(pb, LikeExpression.ILIKE_COMPOSER, "Ab", "a_","\\", true, false, false);
        param(pb, LikeExpression.BLIKE_COMPOSER, "Ab", "a_", "\\",false, false, false);

        param(pb, LikeExpression.ILIKE_COMPOSER, "Ab", "A%", "\\",true, false, false);
        param(pb, LikeExpression.BLIKE_COMPOSER, "Ab", "A%", "=",true, false, false);

        param(pb, LikeExpression.ILIKE_COMPOSER, "A%", "a=%", "=",true, false, false);
        param(pb, LikeExpression.BLIKE_COMPOSER, "A%", "a=%", "=",false, false, false);

        param(pb, LikeExpression.ILIKE_COMPOSER, "a_", "a=_", "=", true, false, false);
        param(pb, LikeExpression.BLIKE_COMPOSER, "a_", "a=_", "=",true, false, false);

        param(pb, LikeExpression.ILIKE_COMPOSER, "C:\\java\\", "_:%\\\\", "\\", true, false, false);
        param(pb, LikeExpression.BLIKE_COMPOSER, "C:\\java\\", "_:%\\\\", "\\",true, false, false);

        param(pb, LikeExpression.ILIKE_COMPOSER, "x===b", "_=====__", "=", false, false, false);

        param(pb, LikeExpression.ILIKE_COMPOSER, "Axydam", "A%d=am", "=", true, false, false); 
        param(pb, LikeExpression.ILIKE_COMPOSER, "Ja%vas", "J_\\%%", "\\", true, false, false);

        param(pb, LikeExpression.ILIKE_COMPOSER, "", "", "\\",true, false, false);
        param(pb, LikeExpression.BLIKE_COMPOSER, "", "", "\\",true, false, false);

        param(pb, LikeExpression.ILIKE_COMPOSER, "", "%", "\\",true,  false, false);
        param(pb, LikeExpression.BLIKE_COMPOSER, "", "%", "\\",true,  false, false);

        param(pb, LikeExpression.ILIKE_COMPOSER, "", "_", "\\",false, false, false);
        param(pb, LikeExpression.BLIKE_COMPOSER, "", "_", "\\",false, false, false);

        param(pb, LikeExpression.ILIKE_COMPOSER, "abc", "", "\\",false, true, false);
        param(pb, LikeExpression.BLIKE_COMPOSER, "abc", "", "\\",false, true, false);

        param(pb, LikeExpression.ILIKE_COMPOSER, "abc=_", "abc===_", "=",true, false, false);

        // underscore as escape
        param(pb, LikeExpression.ILIKE_COMPOSER, "abc_", "abc__", "_", true, false, false);
        param(pb, LikeExpression.ILIKE_COMPOSER, "123a", "123__", "_", false, false, false);
        param(pb, LikeExpression.ILIKE_COMPOSER, "abc%", "abc_%", "_", true, false, false);
        param(pb, LikeExpression.ILIKE_COMPOSER, "abc%def", "abc_%def", "_", true, false,false);
        param(pb, LikeExpression.ILIKE_COMPOSER, "abc_%def", "abc___%def", "_", true, false, false);
        param(pb, LikeExpression.BLIKE_COMPOSER, "axybc", "a%bc", "_", true, false, false);
        param(pb, LikeExpression.ILIKE_COMPOSER, "abcd", "abc_", "_", true, false, true); // exception invalid escape sequence
        param(pb, LikeExpression.ILIKE_COMPOSER, "abcxde", "abc_de", "_", false, false, false);  


        // percent as escape
        param(pb, LikeExpression.ILIKE_COMPOSER, "abc_", "abc%_", "%", true, false, false);
        param(pb, LikeExpression.ILIKE_COMPOSER, "abc%", "abc%%", "%", true, false, false);
        param(pb, LikeExpression.ILIKE_COMPOSER, "abcxyz", "abc%", "%", true, false, true); // % by itself exception
        param(pb, LikeExpression.ILIKE_COMPOSER, "abc", "abc%%", "%", false, false, false); // two %s => not a wildcard anymore
        param(pb, LikeExpression.ILIKE_COMPOSER, "abc", "abc%%", "\\", true, false, false); 
        
        param(pb, LikeExpression.ILIKE_COMPOSER, "ab%c%cde", "ab%%c%%%", "%", true, false, true);  // exception be thrown

        // null as escape, then \\ is used
        param(pb, LikeExpression.ILIKE_COMPOSER, "a%bc_dxyz", "a\\%bc\\_d%", null, true, true, false); // null value source

        // 'a!c' like 'a%!_' vs. 'a_c' like 'a%$__' escape '$'
        param(pb, LikeExpression.ILIKE_COMPOSER, "a!c", "a%!_", "\\", true, false, false);
        param(pb, LikeExpression.ILIKE_COMPOSER, "a_c", "a%$__", "$", true, false, false);

        //'abbbc' like 'a%_' vs  'ac' like 'a%_' vs.
        param(pb, LikeExpression.ILIKE_COMPOSER, "abbbc", "ab%_", "\\", true, false, false);
        param(pb, LikeExpression.ILIKE_COMPOSER, "ac", "a%_", "\\", true, false, false);

        // 'a' like 'a%_' => false
        param(pb, LikeExpression.ILIKE_COMPOSER, "a", "a%_", "\\", false, false, false);

        // "a124b123" LIKE "a%12_"
        param(pb, LikeExpression.ILIKE_COMPOSER, "a124b123", "a%12_", "\\", true, false, false);

        // "abx" LIKE "ab%_x
        param(pb, LikeExpression.ILIKE_COMPOSER, "abx", "ab%_x", "\\", false, false, false);
        param(pb, LikeExpression.ILIKE_COMPOSER, "abx", "a%_x", "\\", true, false, false);
        param(pb, LikeExpression.ILIKE_COMPOSER, "abcx", "a%_x", "\\", true, false, false);

        // "a123b124" LIKE "a%12%";
        param(pb, LikeExpression.ILIKE_COMPOSER, "a123b124", "a%124%", "\\", true, false, false);
        param(pb, LikeExpression.ILIKE_COMPOSER, "a123b124x", "a%124_", "\\", true, false, false);

        //'%' like '%%%_' escape '%'
        param(pb, LikeExpression.ILIKE_COMPOSER, "%", "%%%_", "%", false, false, false);

        return pb.asList();
    }

    private static void param(ParameterizationBuilder pb, ExpressionComposer ex,
            String left, String right,String esc, boolean expected, boolean expectNull, boolean expectExc)
    {
        pb.add(left + " " + ex.toString() + " " + right + ", esca = " + (esc == null ? "NULL" : esc)
                , ex, left, right, esc, expected, expectNull, expectExc );
    }

    @OnlyIfNot("expectExc()")
    @Test
    public void testWithoutExc()
    {
       test();
    }

    @OnlyIf("expectExc()")
    @Test(expected = ValueSourceIsNullException.class)
    public void testWithExc()
    {
        test();
    }

    private void test()
    {
        Expression l = new LiteralExpression(AkType.VARCHAR, left);
        Expression es = new LiteralExpression(esc == null ? AkType.NULL :AkType.VARCHAR, esc == null ? "": esc);
        Expression r;

        if (expectNull)
            r = LiteralExpression.forNull();
        else
            r = new LiteralExpression(AkType.VARCHAR, right);

        Expression top = compose(getComposer(), Arrays.asList(l, r, es));

        if (expectNull)
            assertTrue(top.evaluation().eval().isNull());
        else
            assertTrue (top.evaluation().eval().getBool() == expected);

        already = true;
    }

    public boolean expectExc ()
    {
        return invalidExc;
    }

    @Override
    public boolean alreadyExc ()
    {
        return already;
    }

    @Override
    protected CompositionTestInfo getTestInfo()
    {
        return info;
    }

    @Override
    protected ExpressionComposer getComposer()
    {
        return ex;
    }
}
