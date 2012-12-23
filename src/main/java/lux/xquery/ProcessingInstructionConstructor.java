package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;

public class ProcessingInstructionConstructor extends AbstractExpression {

    public ProcessingInstructionConstructor(AbstractExpression name, AbstractExpression content) {
        super(Type.PROCESSING_INSTRUCTION);
        this.subs = new AbstractExpression[] { name, content };
    }

    @Override
    public AbstractExpression accept(ExpressionVisitor visitor) {
        acceptSubs(visitor);
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ("processing-instruction { ");
        getName().toString(buf);
        buf.append(" } { ");
        if (getContent() != null) {
            getContent().toString(buf);
        }
        buf.append (" }");
    }
    
    private AbstractExpression getName() {
        return subs[0];
    }

    private AbstractExpression getContent() {
        return subs[1];
    }
    
    @Override
    public int getPrecedence () {
        return 0;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
