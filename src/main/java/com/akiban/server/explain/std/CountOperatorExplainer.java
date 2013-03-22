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

package com.akiban.server.explain.std;

import com.akiban.qp.exec.Plannable;
import com.akiban.qp.operator.Operator;
import com.akiban.qp.rowtype.RowType;
import com.akiban.qp.rowtype.ValuesRowType;
import com.akiban.server.explain.*;

public class CountOperatorExplainer extends CompoundExplainer
{
    public CountOperatorExplainer (String opName, RowType inputType, ValuesRowType resultType, Operator inputOp, ExplainContext context)
    {
        super(Type.COUNT_OPERATOR, buildAtts(opName, inputType, resultType, inputOp, context));
    }
    
    private static Attributes buildAtts (String name, RowType inputType, ValuesRowType resultType, Operator inputOp, ExplainContext context)
    {
        Attributes atts = new Attributes();
        
        atts.put(Label.NAME, PrimitiveExplainer.getInstance(name));
        atts.put(Label.INPUT_TYPE, inputType.getExplainer(context));
        atts.put(Label.OUTPUT_TYPE, resultType.getExplainer(context));
        if (inputOp != null)
            atts.put(Label.INPUT_OPERATOR, inputOp.getExplainer(context));
        
        return atts;
    }
}
