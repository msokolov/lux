package lux.xquery;

import lux.ExpressionVisitor;
import lux.xpath.AbstractExpression;

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
    public void toString(StringBuilder buf) {
        for (FLWORClause clause : clauses) {
            clause.toString(buf);
            buf.append(' ');
        }
        buf.append ("return ");
        returnExpr.toString(buf);
    }

}
