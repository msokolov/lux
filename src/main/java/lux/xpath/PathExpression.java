/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xpath;

import lux.xquery.VariableContext;


/**
 * A path expression represents two expressions joined with a "/"
 * @author sokolov
 *
 */
public class PathExpression extends AbstractExpression {
    
    public PathExpression (AbstractExpression lhs, AbstractExpression rhs) {
        super (Type.PATH_EXPRESSION);
        subs = new AbstractExpression[2];
        setSubs (lhs, rhs);
    }
    
    public final AbstractExpression getRHS() {
        return subs[1];
    }
    
    public final AbstractExpression getLHS() {
        return subs[0];
    }

    /**
     * @return 18
     */
    @Override public int getPrecedence () {
        return 18;
    }

    @Override
    public void toString(StringBuilder buf) {
        if (! (subs[0] instanceof Root)) {
            appendSub (buf, subs[0]);
        }
        buf.append('/');
        appendSub(buf, subs[1]);
    }
    
    /**
     * Whenever we see a new absolute context (/, collection(), search()), its dependent 
     * expressions are a possible target for optimization.
     * @return the root of this path, or null if it is not an absolute path.
     */
    @Override
    public AbstractExpression getRoot() {
       return subs[0].getRoot();
    }

    @Override
    public AbstractExpression accept(ExpressionVisitor visitor) {
        acceptSubs(visitor);
        return visitor.visit(this);
    }

    /**
     * @return the expression remaining after removing the left-most sub-expression (the CDR).
     */
    @Override
    public AbstractExpression getTail() {
        AbstractExpression left = subs[0].getTail();
        if (left == null) {
            return subs[1];
        }
        return new PathExpression (left, subs[1]);
    }
    
    /**
     * @return the rightmost step of this path expression
     */
    @Override
    public AbstractExpression getLastContextStep() {
        AbstractExpression expr = subs[1].getLastContextStep();
        if (expr.getType() == Type.PATH_STEP) {
            return expr;
        }
        return subs[0].getLastContextStep();
    }
    

    /**
     * @return the context in which a variable in the LHS of the path is bound, if any
     */
    @Override
    public VariableContext getBindingContext () {
        return subs[0].getBindingContext();
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
