package lux.xquery;

import lux.compiler.ExpressionVisitor;
import lux.xpath.AbstractExpression;

public class TextConstructor extends AbstractExpression {
    
    public TextConstructor (AbstractExpression expression) {
        super (Type.TEXT);
        subs = new AbstractExpression [] { expression };
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ("text { ");
        getContent().toString(buf);
        buf.append (" } ");
    }

    private AbstractExpression getContent() {
        return subs[0];
    }

    @Override
    public int getPrecedence () {
        return 0;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
