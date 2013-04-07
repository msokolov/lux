package lux.xquery;

import lux.xml.QName;
import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;

public class Variable extends AbstractExpression {
    
    private QName name;
    private AbstractExpression value;
    private String typeDesc;
    
    public Variable (QName qname) {
        super (Type.VARIABLE);
        name = qname;
    }

    public Variable (QName qname, String typeDesc) {
        super (Type.VARIABLE);
        name = qname;
        this.typeDesc = typeDesc;
    }
    
    @Override
    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ('$');
        name.toString(buf);
        if (typeDesc != null) {
            buf.append (" as ").append(typeDesc);
        }
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

    public void setName(QName name2) {
        name = name2;
    }

    public void setValue(AbstractExpression value) {
        this.value = value;
    }
    
    public AbstractExpression getValue () {
        return value;
    }
    
    @Override
    public AbstractExpression getRoot () {
        if (value == null) {
            return null;
        }
        return value.getRoot();
    }
    
    /**
     * @return the last context step of the value expression.
     */
    @Override
    public AbstractExpression getLastContextStep () {
        return getValue().getLastContextStep();
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
