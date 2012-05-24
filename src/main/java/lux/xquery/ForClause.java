package lux.xquery;

import lux.ExpressionVisitor;
import lux.xpath.AbstractExpression;

public class ForClause extends FLWORClause {
    
    private Variable var;
    private Variable pos;
    private AbstractExpression seq;

    /**
     * create an XQuery 'for' clause
     * @param var the range variable (for $x)
     * @param pos the position variable (at $n)
     * @param seq the sequence (in ...)
     */
    public ForClause(Variable var, Variable pos, AbstractExpression seq) {
        this.var = var;
        this.pos = pos;
        this.seq = seq;
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ("for ");
        var.toString(buf);
        if (pos != null) {
            buf.append (" at ");
            pos.toString(buf);
        }
        buf.append (" in ");
        seq.toString(buf);
    }

    public AbstractExpression accept(ExpressionVisitor visitor) {
        seq = seq.accept(visitor);
        return seq;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
