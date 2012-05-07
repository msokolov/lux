package lux.lucene;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.Query;

/**
 * This query exists only to serve as a placeholder in an intermediate query compilation
 * phase so that span queries can be printed out in surround query parser language;
 * it doesn't actually query anything.
 */
public class SurroundSpanQuery extends Query {
    
    private Query[] clauses;
    private int slop;
    private boolean inOrder;

    public SurroundSpanQuery(int slop, boolean inOrder, Occur occur, Query ... clauses) {
        this.clauses = clauses;
        this.slop= slop;
        this.inOrder= inOrder;
    }
    
    @Override
    public String toString () {
        StringBuilder buf = new StringBuilder();
        if (slop > 0) {
            buf.append(slop+1);
        }
        if (inOrder) {
            buf.append ('w');
        } else {
            buf.append ('n');
        }
        buf.append ('(');
        if (clauses.length > 0) {
            buf.append (clauses[0].toString());            
        }
        for (int i = 1; i < clauses.length; i++) {
            buf.append (",");
            buf.append (clauses[i].toString());
        }
        buf.append (')');
        return buf.toString();
    }

    @Override
    public String toString(String field) {
        return field + ":(" + toString() + ')';
    }

}
