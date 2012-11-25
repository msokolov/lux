package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;

/**
 * represents xquery conditionals (if, then, else)
 */
public class Conditional extends AbstractExpression {
    
    public Conditional (AbstractExpression condition, AbstractExpression trueAction, AbstractExpression falseAction) {
        super (Type.CONDITIONAL);
        subs = new AbstractExpression[] { condition, trueAction, falseAction };
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        acceptSubs(visitor);
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ("if (");
        getCondition().toString(buf);
        buf.append (") then (");
        getTrueAction().toString(buf);
        if (getFalseAction() != null) {
            buf.append(") else (");
            getFalseAction().toString(buf);
        }
        buf.append (")");
    }
    
    public final AbstractExpression getCondition () {
        return subs[0];
    }

    
    public final AbstractExpression getTrueAction() {
        return subs[1];
    }
    
    public final AbstractExpression getFalseAction() {
        return subs[2];
    }

    @Override
    public int getPrecedence () {
        return 3;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
