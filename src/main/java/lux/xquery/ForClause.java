package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;

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
    public AbstractExpression getSequence() {
        return seq;
    }
    
    @Override
    public void setSequence (AbstractExpression seq) {
        this.seq = seq;
    }
    
    public Variable getVariable () {
        return var;
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

    @Override
    public ForClause accept(ExpressionVisitor visitor) {
        seq = seq.accept(visitor);
        return visitor.visit(this);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
