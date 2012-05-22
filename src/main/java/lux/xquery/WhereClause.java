package lux.xquery;

import lux.ExpressionVisitor;
import lux.xpath.AbstractExpression;

public class WhereClause extends FLWORClause {

    private AbstractExpression predicate;
    
    public WhereClause(AbstractExpression predicate) {
        this.predicate = predicate;
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ("where ");
        predicate.toString(buf);
    }

    public AbstractExpression accept(ExpressionVisitor visitor) {
        predicate = predicate.accept(visitor);
        return predicate;
    }

}
