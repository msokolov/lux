package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.LiteralExpression;

public class SortKey {
    
    private final AbstractExpression key;
    private final LiteralExpression order;
    private final AbstractExpression collation;
    private final boolean emptyLeast;
    
    /**
     * Create a sort key; part of a for clause in a flwor expression
     * 
     * @param key the key to sort by
     * @param order ascending or descending
     * @param collation implementation-defined
     * @param emptyLeast 
     */
    public SortKey (AbstractExpression key, LiteralExpression order, AbstractExpression collation, boolean emptyLeast) {
        this.key = key;
        this.order = order;
        this.collation = collation;
        this.emptyLeast = emptyLeast;
        // language?
        // case order?
    }
    
    public void toString (StringBuilder buf ) {
        key.toString(buf);
        buf.append (' ');
        buf.append (order.getValue());
        if (collation != null) {
            buf.append (" collation ");
            buf.append (collation);
        }
        if (!emptyLeast) {
            buf.append (" empty greatest");
        }
    }
}
