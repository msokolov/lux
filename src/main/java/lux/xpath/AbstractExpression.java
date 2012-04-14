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
        Literal, Root, Dot, FunctionCall, Sequence, UnaryMinus            
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
        for (AbstractExpression sub : getSubs()) {
            sub.accept (visitor);
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

    public boolean isAbsolute() {
        return false;
    }

    /** 
     * If this has a root expression, replace it with the function call expression
     * @param search the search function call to use in place of '/'
     */
    public AbstractExpression replaceRoot(FunCall search) {
        if (subs != null && subs.length > 0) {
            subs[0] = subs[0].replaceRoot(search);
        }
        return this;
    }

}
