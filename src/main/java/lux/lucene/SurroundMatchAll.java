package lux.lucene;

import org.apache.lucene.search.Query;

/**
 * This query exists only to serve as a placeholder in an intermediate query compilation
 * phase.  It prints out a "match all" in surround query parser language;
 * it doesn't actually query anything.
 */
public class SurroundMatchAll extends Query {
    
    private static final SurroundMatchAll INSTANCE = new SurroundMatchAll();
    
    public static final SurroundMatchAll getInstance () {
        return INSTANCE;
    }
    
    @Override
    public String toString(String field) {
        return "{}";
    }

}
