package lux.xpath;

public interface Visitable<T> {
    void accept (Visitor<T> visitor);
}
