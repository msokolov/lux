package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;

public class LetClause extends VariableBindingClause {

    public LetClause(Variable var, AbstractExpression seq) {
        super (var, seq);
    }
    
    @Override
    public void toString(StringBuilder buf) {
        buf.append ("let ");
        getVariable().toString (buf);
        buf.append (" := ");
        getSequence().toString(buf);
    }

    @Override
    public LetClause accept(ExpressionVisitor visitor) {
        setSequence (getSequence().accept(visitor));
        
        return visitor.visit(this);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
