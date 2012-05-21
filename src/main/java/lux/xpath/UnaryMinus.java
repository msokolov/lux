/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xpath;

import lux.ExpressionVisitor;

public class UnaryMinus extends AbstractExpression {

    public UnaryMinus (AbstractExpression operand) {
        super (Type.UnaryMinus);
        subs = new AbstractExpression[] { operand };        
    }
    
    public AbstractExpression getOperand () {
        return subs[0];
    }
    
    @Override
    public void toString(StringBuilder buf) {
        buf.append ('-');
        subs[0].toString(buf);
    }

    public AbstractExpression accept(ExpressionVisitor visitor) {
        subs[0].accept(visitor);
        return visitor.visit(this);
    }
    
    @Override
    public boolean isDocumentOrdered () {
        return false;
    }
}
