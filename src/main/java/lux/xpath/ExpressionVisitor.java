package lux.xpath;

public abstract class ExpressionVisitor {
    private boolean reverse = false;
    /**
     * @return whether the sub-expressions should be visited in reverse (right-to-left)
     * order.
     */
    public boolean isReverse () {
        return reverse;
    }

    public void setReverse (boolean reverse) {
        this.reverse = reverse;
    }
    
    /**
     * @return true if the visit is done; this allows visits to terminate early
     */
    public boolean isDone () {
        return false;
    }

    public abstract AbstractExpression visit (PathStep step);
    public abstract AbstractExpression visit (PathExpression path);
    public abstract AbstractExpression visit (Root root);
    public abstract AbstractExpression visit (Dot dot);
    public abstract AbstractExpression visit (BinaryOperation op);
    public abstract AbstractExpression visit (FunCall func);
    public abstract AbstractExpression visit (LiteralExpression literal);
    public abstract AbstractExpression visit (Predicate predicate);
    public abstract AbstractExpression visit (Sequence predicate);
    public abstract AbstractExpression visit (Subsequence predicate);
    public abstract AbstractExpression visit (SetOperation predicate);
    public abstract AbstractExpression visit (UnaryMinus predicate);
    public abstract AbstractExpression visit (Let let);
    public abstract AbstractExpression visit (Variable variable);
}
