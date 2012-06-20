package lux.xquery;

import lux.ExpressionVisitor;
import lux.xpath.AbstractExpression;

public class DocumentConstructor extends AbstractExpression {
    
    public DocumentConstructor (AbstractExpression content) {
        super (Type.DOCUMENT_CONSTRUCTOR);
        subs = new AbstractExpression[] { content };
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ("document { ");
        subs[0].toString(buf);
        buf.append (" }");
    }

    @Override
    public int getPrecedence () {
        return 0;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
