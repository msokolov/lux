package lux.xpath;

public interface Visitable {
    void accept (ExpressionVisitor visitor);
}
