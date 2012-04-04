package lux.api;

/**
 * An interface that provides iteration of results and a total count.  Serves as a bridge
 * between java collection apis and saxon's XdmValue api.
 */
public interface ResultSet<T> extends Iterable<T> {
    int size();
}
