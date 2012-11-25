package lux.xquery;

import lux.xml.QName;
import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;

public class Variable extends AbstractExpression {
    
    private QName name;
    
    public Variable (QName qname) {
        super (Type.VARIABLE);
        name = qname;
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ('$');
        name.toString(buf);
    }
    
    public QName getQName() {
        return name;
    }

    @Override
    public int getPrecedence () {
        return 0;
    }

    @Override
    public boolean isDocumentOrdered () {
        // it would be nice to check the variable's referent if we can do that at compile time?
        return false;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
