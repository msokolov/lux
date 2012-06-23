/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xpath;

import lux.compiler.ExpressionVisitor;

/**
 * represents numeric literal predicates like [1]; last-predicates like
 * [last()] and calls to the subsequence(expr,integer,integer) function.
 * 
 * @author sokolov
 *
 */
public class Subsequence extends AbstractExpression {

    public Subsequence (AbstractExpression sequence, AbstractExpression start, AbstractExpression length) {
        super (Type.SUBSEQUENCE);
        subs = new AbstractExpression[] { sequence, start, length };
    }
    
    public Subsequence (AbstractExpression sequence, AbstractExpression start) {
        super (Type.SUBSEQUENCE);
        subs = new AbstractExpression[] { sequence, start };
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        acceptSubs(visitor);
        return visitor.visit(this);
    }
    
    public AbstractExpression getSequence() {
        return subs[0];
    }
    
    public void setSequence (AbstractExpression ae) {
        subs[0] = ae;
    }

    public AbstractExpression getStartExpr () {
        return subs[1];
    }
    
    public void setStartExpr (AbstractExpression ae) {
        subs[1] = ae;
    }

    public AbstractExpression getLengthExpr () {
        return subs.length > 2 ? subs[2] : null;
    }
    
    /**
     * @param ae the expression to use as the length expression
     * @throws ArrayIndexOutOfBoundsException if there wasn't already a length expression
     */
    public void setLengthExpr (AbstractExpression ae) {
        subs[2] = ae;
    }
    
    @Override
    public boolean isAbsolute () {
        return getSequence().isAbsolute();
    }

    /**
     * @return the precedence of comma (,) or predicate ([]), depending
     * on the child expressions.
     */
    @Override
    public int getPrecedence () {
        if (getLengthExpr() == null) {
            return 1;
        } else {
            return 19;
        }
    }

    @Override
    public void toString(StringBuilder buf) {
        if (getLengthExpr() == null) {
            buf.append ("subsequence(");
            getSequence().toString(buf);
            buf.append (',');
            getStartExpr().toString(buf);
            buf.append (')');
        }
        else if (getLengthExpr().equals(LiteralExpression.ONE) && 
                (getStartExpr().getType() == Type.LITERAL ||
                        getStartExpr().equals(FunCall.LastExpression))) 
        {
            appendSub(buf, getSequence());
            buf.append ("[");
            getStartExpr().toString(buf);
            buf.append (']');
        }
        else  {
            buf.append ("subsequence(");
            getSequence().toString(buf);
            buf.append (',');
            getStartExpr().toString(buf);
            buf.append (',');
            getLengthExpr().toString(buf);
            buf.append (')');
        }
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
