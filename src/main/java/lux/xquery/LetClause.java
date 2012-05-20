package lux.xquery;

import lux.xpath.AbstractExpression;

public class LetClause extends FLWORClause {

    private Variable var;
    private AbstractExpression seq;
    
    public LetClause(Variable var, AbstractExpression seq) {
        this.var = var;
        this.seq = seq;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder ();
        buf.append ("let ");
        buf.append (var.toString());
        buf.append (" := ");
        buf.append(seq.toString());
        return buf.toString();
    }

}
