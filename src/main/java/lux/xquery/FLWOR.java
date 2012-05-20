package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;

public class FLWOR extends AbstractExpression {
    
    private final FLWORClause[] clauses;
    private final AbstractExpression returnExpr;

    public FLWOR (AbstractExpression returnExpression, FLWORClause... clauses) {
        super (Type.FLWOR);
        this.clauses = clauses;
        this.returnExpr = returnExpression;
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        for (FLWORClause clause : clauses) {
            buf.append(clause.toString());
            buf.append(' ');
        }
        buf.append ("return ");
        buf.append(returnExpr.toString());
        return buf.toString();
    }

}
