package lux.xpath;



/**
 * An abstract XPath 2.0 expression.
 * 
 * This class and its subclasses represent XPath expressions.  Their toString() methods return valid XPath.
 */

public abstract class AbstractExpression implements Visitable {
    
    protected AbstractExpression subs[];

    enum Type {
        PathExpression, PathStep, Predicate, Binary, SetOperation,
        // these are types of Binary: we'll split them out when we need to
        // SetOperation, Comparison, AtomicComparison, MathOperation,
        Literal, Root, Dot, FunctionCall, Sequence, UnaryMinus, Subsequence,
        Let, Variable
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
        for (int i = 0; i < subs.length; i++) {
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

    /** Subclasses must implement the toString() method 
    * @return the expression, as valid XPath 
    */
    public abstract String toString();

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
     * returns true iff all its subs return true.  Warning: incorrect results may occur if 
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
     * If this has a root expression, replace it with the function call expression
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
     * @return the tail of this expression - only has meaning for PathExpressions, which strip off the 
     * return everything after the leftmost step.
     */
    public AbstractExpression getTail() {
        return null;
    }

    public enum Direction { Left, Right };

}
