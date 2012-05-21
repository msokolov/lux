/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xpath;

import lux.ExpressionVisitor;
import lux.xpath.BinaryOperation.Operator;

public class SetOperation extends AbstractExpression {
    
    private final Operator operator;    
    
    public SetOperation (Operator operator, AbstractExpression ... ops) {
        super (Type.SetOperation);
        subs = ops;
        this.operator = operator;
    }
    
    public void toString (StringBuilder buf) {
        buf.append('(');
        Sequence.appendSeqContents(buf, subs, ' ' + operator.toString() + ' ');
        buf.append (')');
    }
    
    public AbstractExpression[] getsubs() {
        return subs;
    }
    
    public Operator getOperator () {
        return operator;
    }

    public AbstractExpression accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        return visitor.visit(this);
    }
}
