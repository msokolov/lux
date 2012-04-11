package lux.xpath;

public interface Visitor<T> {
    void visit(T visitable);
}
