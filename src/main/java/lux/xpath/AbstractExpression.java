package lux.xpath;

import lux.xquery.VariableContext;


/**
 * An abstract XPath or XQuery expression.
 * 
 * This class and its subclasses represent XPath expressions.  Their
 * toString() methods return valid XPath.
 */

public abstract class AbstractExpression implements Visitable {
    
    protected AbstractExpression sup;       // the enclosing (parent) expression, if any
    protected AbstractExpression subs[];    // enclosed (child) expressions, or a 0-length array, or null (do we need to make this consistent?)

    public enum Type {
        PATH_EXPRESSION, PATH_STEP, PREDICATE, BINARY_OPERATION, SET_OPERATION,
        LITERAL, ROOT, DOT, FUNCTION_CALL, SEQUENCE, UNARY_MINUS, SUBSEQUENCE,
        LET, VARIABLE, COMPUTED_ELEMENT, ELEMENT, ATTRIBUTE, TEXT, FLWOR, CONDITIONAL, COMMENT,
        DOCUMENT_CONSTRUCTOR, PROCESSING_INSTRUCTION, SATISFIES, INSTANCE_OF, CASTABLE, TREAT
    }

    private final Type type;
    
    protected AbstractExpression (Type type) {
        this.type = type;
    }

    /** Most types will correspond one-one
     * with a subclass of AbstractExpression, but this
     * enumerated value provides an integer equivalent that should be
     * useful for efficient switch operations, encoding and the like.
     * TODO: determine if this is just a waste of time; we could be using instanceof?
     * @return the type of this expression
     */
    public Type getType () {
        return type;
    }
    
    public void acceptSubs (ExpressionVisitor visitor) {
        for (int i = 0; i < subs.length && !visitor.isDone(); i++) {
            int j = visitor.isReverse() ? (subs.length-i-1) : i;
            AbstractExpression sub = subs[j].accept (visitor);
            if (sub != subs[j]) {
                subs[j]= sub;
            }
        }
    }
    
    /**
     * @return the super (containing) expression, or null if this is the outermost expression in its tree
     */
    public AbstractExpression getSuper() {
        return sup;
    }

    /**
     * @return the sub-expressions of this expression.
     */
    public AbstractExpression [] getSubs() {
        return subs;
    }
    
    protected void setSubs (AbstractExpression ... subExprs) {
        subs = subExprs;
        for (AbstractExpression sub : subs) {
            sub.sup = this;
        }
    }

    /** Each subclass must implement the toString(StringBuilder) method by
     * rendering itself as a syntatically valid XPath/XQuery expression in
     * the given buffer.
     * @param buf
     */
    public abstract void toString(StringBuilder buf);

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        toString (buf);
        return buf.toString();
    }

    /**
     * @return the root of this expression: this will either be a Root(/), a function returning document nodes, 
     * or null.
     */
    public AbstractExpression getRoot () {
       return null; 
    }
    
    /**
     * @return whether this expression is a Root or another expression that introduces
     * a new query scope, such as a PathExpression beginning with a Root (/), or a subsequence
     * of another absolute expression.  This method returns false, supplying the common default.
     */
    public boolean isAbsolute() {
        return getRoot() != null;
    }
    
    /**
     * @return whether this expression is proven to return results in document order.  This method 
     * returns true iff all its subs return true, or it has none.  Warning: incorrect results may occur if 
     * document-ordering is falsely asserted.
     */
    public boolean isDocumentOrdered() {
        if (subs != null) {
            for (AbstractExpression sub : subs) {
                if (!sub.isDocumentOrdered())
                    return false;
            }
        }
        return true;
    }
    
    /** 
     * If this has absolute subexpressions, replace them with the replacement expression
     * (see {@link Root#replaceRoot(AbstractExpression)}
     * @param replacement the expression to use in place of '/'
     * @return this 
     */
    public AbstractExpression replaceRoot(AbstractExpression replacement) {
        if (subs != null) {
            for (int i = 0; i < subs.length; i++) {
                AbstractExpression replaced = subs[i].replaceRoot(replacement);
                if (replaced != subs[i]) {
                    subs[i] = replaced;
                }
            }
        }
        return this;
    }
    
    /**
     * append the sub-expression to the buffer, wrapping it in parentheses if its precedence is
     * lower than or equal to this expression's.  We need parens when precedence is equal because
     * otherwise operations simply group left or right, but we have the actual grouping encoded 
     * in the expression tree and need to preserve that.
     * 
     * Note: we can't just blindly wrap everything in parentheses because parens have special meaning
     * in some XPath expressions where they can introduce document-ordering.
     * 
     * @param buf the buffer to append to
     * @param sub the sub-expression
     */
    protected void appendSub(StringBuilder buf, AbstractExpression sub) {
        if (sub.getPrecedence() <= getPrecedence()) {
            buf.append ('(');
            sub.toString(buf);
            buf.append (')');            
        } else {
            sub.toString(buf);
        }
    }
    
    /**
     * @return the tail of this expression; ie everything after the head is removed, which is null 
     * unless this is a PathExpression {@link PathExpression#getTail}.
     */
    public AbstractExpression getTail() {
        return null;
    }

    /**
     * This method is called by the optimizer in order to determine an element or attribute QName (or wildcard) against which 
     * some expression is being compared, in order to generate an appropriate text query.
     * @return the rightmost path step in the context of this expression.
     */
    public AbstractExpression getLastContextStep () {
        return this;
    }
    
    /**
     * If this expression depends "directly" on a variable, return that variable's binding context: a for or let clause,
     * or a global variable definition. This recurses through variables, so if there are aliases it retrieves the ultimate
     * context.  We need to define directly dependent precisely; what it's used for is to determine with an order by 
     * expression is dependent on a for-variable, and ultimately whether an order by optimization can be applied. 
     * @return the binding context of the variable on which this expression depends, or null
     */
    public VariableContext getBindingContext () {
        return null;
    }

    /**
     * @return a number indicating the *outer* precedence of this expression.
     * Expressions with lower precedence numbers have lower
     * precedence, ie bind more loosely, than expressions with higher
     * precedence. Expressions with no sub-expressions are assigned a high
     * precedence.  Complex expressions can be seen as having an inner and an outer
     * precedence; for example function call expressions behave as a comma with regard 
     * to their sub-expressions, the arguments, and like parentheses to their enclosing expression.
     */
    public abstract int getPrecedence ();

    /**
     * @param other another expression
     * @return whether the two expressions are of the same type and share the same local properties
     */
    public boolean equivalent (AbstractExpression other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (! (getClass().isAssignableFrom(other.getClass()))) {
            return false;
        }
        return propEquals ((AbstractExpression) other);
    }

    /**
     * @param other another expression
     * @return whether this expression is query-geq (fgreater-than-or-equal) to the other, in the sense
     * that for all contexts c, exists(other|c) => exists(this|c). In particular, this implementation tests that 
     * the two expressions are of the same type and have local properties consistent with geq, by calling
     * propGreaterEqual.
     */
    public boolean geq (AbstractExpression other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (! (getClass().isAssignableFrom(other.getClass()))) {
            return false;
        }
        return propGreaterEqual ((AbstractExpression) other);
    }
    
    /**
    	Traverse downwards from queryExpr and fieldExpr, comparing for equivalence until one bottoms out,
    	ignoring fromExpr (since it has already been checked).
    	@param queryExpr
    	@param fieldExpr
    	@return whether fieldExpr >= queryExpr
     */
    public boolean matchDown (AbstractExpression fieldExpr, AbstractExpression fromExpr) {
    	if (fieldExpr == fromExpr) {
    		return true;
    	}
    	if (! fieldExpr.geq(this)) {
    		// if fieldExpr does not encompass this at least formally, it is too restrictive
    		return false;
		}
		// all of queryExpr's subs *must* return a value (for a
		// non-empty result), so a necessary condition for fieldExpr
		// >= queryExpr is that every sub of fieldExpr match *some*
		// sub of queryExpr
		AbstractExpression[] fsubs = fieldExpr.getSubs();
		if (fsubs == null) {
			return subs == null || subs.length == 0 || isRestrictive();
		}
		AbstractExpression qsubMatched = null;
		OUTER: for (AbstractExpression fsub : fsubs) {
			// TODO: skip already matched fsub
			if (fsub == fromExpr) {
				continue;
			}
			// FIXME -- this has to work differently for PathExpressions than it
			// does for Booleans (say) -- in the former
			// case the order matches -- we can't just be matching (a/b) against
			// (b/a)
			for (AbstractExpression sub : subs) {
				if (sub.matchDown(fsub, null)) {
					qsubMatched = sub;
					continue OUTER;
				}
			}
			// no equivalent sub found
			return false;
		}
		if (!isRestrictive()) {
			// at least one of queryExpr's children must return a value, so
			// in addition it is necessary that every child of queryExpr be
			// matched by some child of fieldExpr
			OUTER: for (AbstractExpression sub : subs) {
				if (sub == qsubMatched) {
					continue;
				}
				for (AbstractExpression fsub : fsubs) {
					if (sub.matchDown(fsub, null)) {
						continue OUTER;
					}
				}
				return false;
			}
		}
		return true;
	}

    /**
     * @return a hashcode that is consistent with {@link #equivalent(AbstractExpression)}
     */
    int equivHash () {
        return type.ordinal();
    }
    
    /**
     * @param oex another expression
     * @return whether the other expression and this one have all the same local properties
     */
    protected boolean propEquals (AbstractExpression oex) {
        return (oex.getType() == type);
    }
    
    /**
     * @param oex another expression of the same type as this
     * @return whether the expressions' properties imply: <code>this ge oex</code>
     */
    public boolean propGreaterEqual (AbstractExpression oex) {
        return propEquals(oex);
    }
    
    public boolean deepEquals (AbstractExpression oex) {
    	if (! equivalent(oex)) {
    		return false;
    	}
        if (subs == oex.subs) {
        	return true;
        }
        if (subs == null || oex.subs == null) {
        	return false;
        }
        if (subs.length != oex.subs.length) {
        	return false;
        }
        for (int i = 0; i < subs.length; i++) {
        	if (! (subs[i].deepEquals(oex.subs[i]))) {
        		return false;
        	}
        }
        return true;
    }

    /**
     * An expression is restrictive when any empty sub implies the expression is empty.
     * In other words, restrictive expressions only return results when all of their 
     * subs are non-empty.  Eg: and, intersect, predicate, path step.
     */
    public boolean isRestrictive () {
        return false;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
