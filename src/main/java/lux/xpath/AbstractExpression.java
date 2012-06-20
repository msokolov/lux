package lux.xpath;

import lux.ExpressionVisitor;

/**
 * An abstract XPath or XQuery expression.
 * 
 * This class and its subclasses represent XPath expressions.  Their
 * toString() methods return valid XPath.
 */

public abstract class AbstractExpression implements Visitable {
    
    protected AbstractExpression subs[];

    public enum Type {
        PATH_EXPRESSION, PATH_STEP, PREDICATE, BINARY_OPERATION, SET_OPERATION,
        LITERAL, ROOT, DOT, FUNCTION_CALL, SEQUENCE, UNARY_MINUS, SUBSEQUENCE,
        LET, VARIABLE, ELEMENT, ATTRIBUTE, TEXT, FLWOR, CONDITIONAL, COMMENT,         
        DOCUMENT_CONSTRUCTOR, PROCESSING_INSTRUCTION, SATISFIES, INSTANCE_OF
    };

    private final Type type;
    
    protected AbstractExpression (Type type) {
        this.type = type;
    }

    /** The type of this expression; most types will correspond one-one
     * with a Java class which must be a subclass of AbstractExpression, but this
     * enumerated value provides an integer equivalent that should be
     * useful for efficient switch operations, encoding and the like.
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
     * The sub-expressions of this expression. Most have 0, 1, or 2.  Only
     * functions can have variable numbers of sub-expressions (arguments).
     */
    public AbstractExpression [] getSubs() {
        return subs;
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
     * @return whether this expression is a Root or another expression that introduces
     * a new query scope, such as a PathExpression beginning with a Root (/), or a subsequence
     * of another absolute expression.  This method returns false, supplying the common default.
     */
    public boolean isAbsolute() {
        return false;
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
     * If this has an absolute subexpression, replace it with the function call expression
     * 
     * @param search the search function call to use in place of '/'
     */
    public AbstractExpression replaceRoot(FunCall search) {
        if (subs != null && subs.length > 0) {
            subs[0] = subs[0].replaceRoot(search);
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
     * @return the tail of this expression - null unless this is a PathExpression {@see PathExpression#getTail}.
     */
    public AbstractExpression getTail() {
        return null;
    }

    /**
     * @return the rightmost sub-expression of this expression - this, unless this is a PathExpression {@see PathExpression#getRightmost}.
     */
    public AbstractExpression getRightmost () {
        return this;
    }

    public enum Direction { Left, Right };

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

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
