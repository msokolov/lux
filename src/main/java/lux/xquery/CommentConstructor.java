package lux.xquery;

import lux.ExpressionVisitor;
import lux.xpath.AbstractExpression;

public class CommentConstructor extends AbstractExpression {

    private final AbstractExpression content;
    
    public CommentConstructor (AbstractExpression abstractExpression) {
        super (Type.Comment);
        this.content = abstractExpression;
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append("comment { ");
        content.toString(buf);
        buf.append(" }");
    }

    @Override
    public int getPrecedence () {
        return 100;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
