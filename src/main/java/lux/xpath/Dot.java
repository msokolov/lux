package lux.xpath;


public class Dot extends AbstractExpression {
    
    public Dot () {
        super (Type.DOT);
        subs = new AbstractExpression[0];
    }
    
    @Override
    public void toString(StringBuilder buf) {
        buf.append ('.');
    }
    
    @Override
    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }

    @Override
    public int getPrecedence () {
        return 100;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
