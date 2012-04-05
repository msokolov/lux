package lux.xquery;

/**
 * An abstract expression
 */

public class Aex {

    enum Type {
        PathStep, Predicate, Unary, Binary, 
            SetOperation, Comparison, AtomicComparison, MathOperation,
            Literal, Root, Dot, FunctionCall
            };

    private final Type type;

    /** The type of this expression; most types will correspond one-one
     * with a Java class which must be a subclass of Aex, but this
     * enumerated value provides an integer equivalent that should be
     * useful for efficient switch operations, encoding and the like.
     */
    public getType () {
        return type;
    }

    /**
     * The sub-expressions of this expression. Most have 0, 1, or 2.  Only
     * functions can have variable numbers of sub-expressions (arguments).
     */
    // public abstract Aex [] getSubs();

    // Aex optimize();

    // as xpath/xquery
    // String toString();

}
