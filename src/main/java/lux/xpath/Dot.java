package lux.xpath;


public class Dot extends AbstractExpression {
    
    private static final Dot instance = new Dot();

    protected Dot () {
        super (Type.DOT);
    }
    
    public static Dot getInstance() {
        return instance;
    }
    
    @Override
    public void toString(StringBuilder buf) {
        buf.append ('.');
    }
    
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
