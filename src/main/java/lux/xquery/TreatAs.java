package lux.xquery;

import lux.xml.ValueType;
import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;
import lux.xpath.NodeTest;

public class TreatAs extends AbstractExpression {

    private final ValueType type;
    private final NodeTest nodeTest;
    private final String occurrence;
    
    public TreatAs(AbstractExpression expr, ValueType type, String occurrence) {
        super(Type.TREAT);
        subs = new AbstractExpression[] { expr };
        this.type = type;
        this.nodeTest = null;
        this.occurrence = occurrence;
    }
    
    public TreatAs(AbstractExpression expr, NodeTest nodeTest, String occurrence) {
        super(Type.TREAT);
        subs = new AbstractExpression[] { expr };
        this.type = null;
        this.nodeTest = nodeTest;
        this.occurrence = occurrence;
    }

    @Override
    public AbstractExpression accept(ExpressionVisitor visitor) {
        acceptSubs(visitor);
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        appendSub(buf, subs[0]);
        buf.append(" treat as ");
        if (nodeTest != null) {
            buf.append(nodeTest.toString());
        } else {
            buf.append (type.toString());
        }
        buf.append(occurrence);
    }

    @Override
    public int getPrecedence() {
        return 13;
    }

    @Override
    public VariableContext getBindingContext () {
        return subs[0].getBindingContext();
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
