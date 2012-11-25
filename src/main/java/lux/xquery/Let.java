package lux.xquery;

import lux.xml.QName;
import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;

public class Let extends AbstractExpression {

    private QName name;
    
    public Let (QName name, AbstractExpression assignment, AbstractExpression returnExp) {
        super (Type.LET);
        subs = new AbstractExpression [] { assignment, returnExp };
        this.name = name;
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append("let $");
        name.toString (buf);
        buf.append (" := ");
        appendSub (buf, getAssignment());
        buf.append (" return ");
        getReturn().toString(buf);
    }
    
    public QName getName () {
        return name;
    }
    
    public AbstractExpression getAssignment () {
        return subs[0];
    }
    
    public AbstractExpression getReturn () {
        return subs[1];
    }
    
    @Override
    public boolean isDocumentOrdered () {
        return getReturn().isDocumentOrdered();
    }

    @Override
    public int getPrecedence () {
        return 2;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
