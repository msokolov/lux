package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;

public class LetClause extends FLWORClause {

    private Variable var;
    private AbstractExpression seq;
    
    public LetClause(Variable var, AbstractExpression seq) {
        this.var = var;
        this.seq = seq;
    }
    
    public Variable getVariable () {
        return var;
    }
    
    @Override
    public AbstractExpression getSequence() {
        return seq;
    }
    
    @Override
    public void setSequence (AbstractExpression seq) {
        this.seq = seq;
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ("let ");
        var.toString (buf);
        buf.append (" := ");
        seq.toString(buf);
    }

    @Override
    public LetClause accept(ExpressionVisitor visitor) {
        seq = seq.accept(visitor);
        return visitor.visit(this);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
