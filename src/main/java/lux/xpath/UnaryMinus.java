package lux.xpath;


public class UnaryMinus extends AbstractExpression {

    public UnaryMinus (AbstractExpression operand) {
        super (Type.UNARY_MINUS);
        subs = new AbstractExpression[] { operand };        
    }
    
    public AbstractExpression getOperand () {
        return subs[0];
    }
    
    @Override
    public void toString(StringBuilder buf) {
        buf.append ('-');
        appendSub(buf, subs[0]);
    }

    public AbstractExpression accept(ExpressionVisitor visitor) {
        subs[0].accept(visitor);
        return visitor.visit(this);
    }
    
    /**
     * @return 16
     */
    @Override public int getPrecedence () {
        return 16;
    }

    @Override
    public boolean isDocumentOrdered () {
        return false;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
