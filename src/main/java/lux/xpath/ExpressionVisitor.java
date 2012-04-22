package lux.xpath;

public abstract class ExpressionVisitor {
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
