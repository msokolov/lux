package lux.xpath;

public abstract class ExpressionVisitor {
    public abstract void visit (PathStep step);
    public abstract void visit (PathExpression path);
    public abstract void visit (Root root);
    public abstract void visit (Dot dot);
    public abstract void visit (BinaryOperation op);
    public abstract void visit (FunCall func);
    public abstract void visit (LiteralExpression literal);
    public abstract void visit (Predicate predicate);
    public abstract void visit (Sequence predicate);
    public abstract void visit (SetOperation predicate);
    public abstract void visit (UnaryMinus predicate);
}
