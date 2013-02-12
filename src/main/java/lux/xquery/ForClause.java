package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;

public class ForClause extends VariableBindingClause {
    
    private Variable pos;

    /**
     * create an XQuery 'for' clause
     * @param var the range variable (for $x)
     * @param pos the position variable (at $n)
     * @param seq the sequence (in ...)
     */
    public ForClause(Variable var, Variable pos, AbstractExpression seq) {
        super (var, seq);
        this.pos = pos;
    }
    
    @Override
    public void toString(StringBuilder buf) {
        buf.append ("for ");
        getVariable().toString(buf);
        if (pos != null) {
            buf.append (" at ");
            pos.toString(buf);
        }
        buf.append (" in ");
        getSequence().toString(buf);
    }

    @Override
    public ForClause accept(ExpressionVisitor visitor) {
        setSequence (getSequence().accept(visitor));
        return visitor.visit(this);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

