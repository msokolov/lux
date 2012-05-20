package lux.xquery;

import lux.xpath.AbstractExpression;

public class WhereClause extends FLWORClause {

    private AbstractExpression predicate;
    
    public WhereClause(AbstractExpression predicate) {
        this.predicate = predicate;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder ();
        buf.append ("where ");
        buf.append (predicate.toString());
        return buf.toString();
    }

}
