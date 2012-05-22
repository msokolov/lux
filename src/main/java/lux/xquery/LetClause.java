package lux.xquery;

import lux.ExpressionVisitor;
import lux.xpath.AbstractExpression;

public class LetClause extends FLWORClause {

    private Variable var;
    private AbstractExpression seq;
    
    public LetClause(Variable var, AbstractExpression seq) {
        this.var = var;
        this.seq = seq;
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ("let ");
        var.toString (buf);
        buf.append (" := ");
        seq.toString(buf);
    }

    public AbstractExpression accept(ExpressionVisitor visitor) {
        seq = seq.accept(visitor);
        return seq;
    }

}
