package lux.xpath;


public class Predicate extends AbstractExpression {
    
    public Predicate (AbstractExpression base, AbstractExpression filter) {
        super (Type.PREDICATE);
        subs = new AbstractExpression[] { base, filter };
    }
    
    public void toString (StringBuilder buf) {
        appendSub (buf, subs[0]);
        buf.append ('[');
        subs[1].toString(buf);
        buf.append (']');
    }
    
    public final AbstractExpression getBase() {
        return subs[0];
    }

    public final AbstractExpression getFilter() {
        return subs[1];
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        return visitor.visit(this);
    }
    
    /**
     * @return 19
     */
    @Override public int getPrecedence () {
        return 19;
    }

    @Override    
    public boolean isAbsolute () {
        return getBase().isAbsolute();
    }
    
    @Override
    public boolean isDocumentOrdered () {
        return getBase().isDocumentOrdered();
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
