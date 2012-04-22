package lux.xpath;

public abstract class ExpressionVisitorBase extends ExpressionVisitor {

    @Override
    public AbstractExpression visit(PathStep step) {
        return step;
    }

    @Override
    public AbstractExpression visit(PathExpression path) {
        return path;
    }
    
    @Override
    public AbstractExpression visit(Let let) {
        return let;
    }
    
    @Override
    public AbstractExpression visit(Variable var) {
        return var;
    }

    @Override
    public AbstractExpression visit(Root root) {
        return root;
    }

    @Override
    public AbstractExpression visit(Dot dot) {
        return dot;
    }

    @Override
    public AbstractExpression visit(BinaryOperation op) {
        return op;
    }

    @Override
    public AbstractExpression visit(FunCall func) {
        return func;
    }

    @Override
    public AbstractExpression visit(LiteralExpression literal) {
        return literal;
    }

    @Override
    public AbstractExpression visit(Predicate predicate) {
        return predicate;
    }

    @Override
    public AbstractExpression visit(Sequence seq) {
        return seq;
    }
    
    @Override
    public AbstractExpression visit(Subsequence subseq) {
        return subseq;
    }

    @Override
    public AbstractExpression visit(SetOperation setop) {
        return setop;
    }

    @Override
    public AbstractExpression visit(UnaryMinus unaryMinus) {
        return unaryMinus;
    }

}
