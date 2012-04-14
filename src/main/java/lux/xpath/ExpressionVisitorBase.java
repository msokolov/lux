package lux.xpath;

public abstract class ExpressionVisitorBase extends ExpressionVisitor {

    @Override
    public void visit(PathStep step) {
    }

    @Override
    public void visit(PathExpression path) {
    }

    @Override
    public void visit(Root root) {
    }

    @Override
    public void visit(Dot dot) {
    }

    @Override
    public void visit(BinaryOperation op) {
    }

    @Override
    public void visit(FunCall func) {
    }

    @Override
    public void visit(LiteralExpression literal) {
    }

    @Override
    public void visit(Predicate predicate) {
    }

    @Override
    public void visit(Sequence predicate) {
    }

    @Override
    public void visit(SetOperation predicate) {
    }

    @Override
    public void visit(UnaryMinus predicate) {
    }

}
