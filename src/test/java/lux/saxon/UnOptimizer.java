package lux.saxon;

import lux.xpath.AbstractExpression;
import lux.xpath.BinaryOperation;
import lux.xpath.Dot;
import lux.xpath.ExpressionVisitor;
import lux.xpath.FunCall;
import lux.xpath.LiteralExpression;
import lux.xpath.PathExpression;
import lux.xpath.PathStep;
import lux.xpath.Predicate;
import lux.xpath.QName;
import lux.xpath.Root;
import lux.xpath.Sequence;
import lux.xpath.SetOperation;
import lux.xpath.UnaryMinus;

public class UnOptimizer extends ExpressionVisitor {

    private static final QName luxSearchQName = new QName ("lux", "search", "lux");
    
    public AbstractExpression unoptimize (AbstractExpression aex) {
        aex.accept(this);
        return aex;
    }

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
        if (func.getQName().equals(luxSearchQName)) {
            func.getSubs()[0] = new LiteralExpression ("*.*");
        }
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
