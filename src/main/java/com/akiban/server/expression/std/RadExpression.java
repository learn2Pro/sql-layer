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

package com.akiban.server.expression.std;

import com.akiban.server.error.WrongExpressionArityException;
import com.akiban.server.expression.Expression;
import com.akiban.server.expression.ExpressionComposer;
import com.akiban.server.expression.ExpressionEvaluation;
import com.akiban.server.expression.ExpressionType;
import com.akiban.server.expression.TypesList;
import com.akiban.server.service.functions.Scalar;
import com.akiban.server.types.AkType;
import com.akiban.server.types.NullValueSource;
import com.akiban.server.types.ValueSource;
import com.akiban.sql.StandardException;

public class RadExpression extends AbstractUnaryExpression
{
    @Scalar("radians")
    public static final ExpressionComposer COMPOSER = new UnaryComposer()
    {

        @Override
        public ExpressionType composeType(TypesList argumentTypes) throws StandardException
        {
            if (argumentTypes.size() != 1)
                throw new WrongExpressionArityException(1, argumentTypes.size());
            argumentTypes.setType(0, AkType.DOUBLE);
            return argumentTypes.get(0);
        }

        @Override
        protected Expression compose(Expression argument, ExpressionType argType, ExpressionType resultType)
        {
            return new RadExpression(argument);
        }
    };
    
    private static class InnerEvaluation extends AbstractUnaryExpressionEvaluation
    {
        private static final double FACTOR = Math.PI / 180;
        
        public InnerEvaluation(ExpressionEvaluation operand)
        {
            super(operand);
        }
        
        @Override
        public ValueSource eval()
        {
            ValueSource source = operand();
            if (source.isNull())
                return NullValueSource.only();
            
            valueHolder().putDouble(FACTOR * source.getDouble());
            return valueHolder();
        }
    }
    
    RadExpression (Expression arg)
    {
        super(AkType.DOUBLE, arg);
    }
    
    @Override
    public String name()
    {
        return "RADIANS";
    }

    @Override
    public ExpressionEvaluation evaluation()
    {
        return new InnerEvaluation(operandEvaluation());
    }
}
