package lux.xpath;

import lux.api.ValueType;
import lux.xpath.PathStep.Axis;

import org.junit.Test;

public class TestVisitor {

    @Test public void testVisit () {
        PathOptimizer opt = new PathOptimizer ();
        PathExpression expr = new PathExpression (
                new Root(),
                new Predicate(
                new PathStep (Axis.Child, new NodeTest (ValueType.ELEMENT, new QName("foo"))),
                new LiteralExpression (1))
                );
        expr.accept(opt);
    }
}
