package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;

public class CommentConstructor extends AbstractExpression {

    public CommentConstructor (AbstractExpression abstractExpression) {
        super (Type.COMMENT);
        this.subs = new AbstractExpression[] { abstractExpression };
    }
    
    @Override
    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append("comment { ");
        subs[0].toString(buf);
        buf.append(" }");
    }

    @Override
    public int getPrecedence () {
        return 0;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
