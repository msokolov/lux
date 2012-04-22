package lux.xpath;

public interface Visitable {
    AbstractExpression accept (ExpressionVisitor visitor);
}
