package lux.xpath;


public class Predicate extends AbstractExpression {
    
    public Predicate (AbstractExpression base, AbstractExpression filter) {
        super (Type.PREDICATE);
        setSubs (base, filter);
    }
    
    @Override
    public void toString (StringBuilder buf) {
        appendSub (buf, subs[0]);
        buf.append ('[');
        subs[1].toString(buf);
        buf.append (']');
    }
    
    /**
     * @return the base of the predicate expression (the part that is tested by the predicate filter)
     */
    public final AbstractExpression getBase() {
        return subs[0];
    }

    /**
     * @return the filter of the predicate expression (the part that tests the base expression)
     */
    public final AbstractExpression getFilter() {
        return subs[1];
    }

    public final void setFilter(AbstractExpression filter) {
        subs[1] = filter;
    }
    
    @Override
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
    public AbstractExpression getRoot() {
        return getBase().getRoot();
    }
    
    @Override
    public boolean isDocumentOrdered () {
        return getBase().isDocumentOrdered();
    }

    @Override
    public boolean isRestrictive () {
        return true;
    }

    /**
     * @param other another expression
     * @return whether the two expressions are s.t. this expr is non-empty
     * whenever (for whichever contexts) the other one is.
     */
    public boolean geq (AbstractExpression other) {
        return other instanceof PathExpression || other instanceof Predicate;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
