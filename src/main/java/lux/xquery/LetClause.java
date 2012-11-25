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
    
    public AbstractExpression getSequence() {
        return seq;
    }
    
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

    public AbstractExpression accept(ExpressionVisitor visitor) {
        seq = seq.accept(visitor);
        return seq;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
