package lux.xquery;

import lux.xpath.AbstractExpression;

public class ForClause extends FLWORClause {
    
    private Variable var;
    private Variable pos;
    private AbstractExpression seq;

    /**
     * create an XQuery 'for' clause
     * @param var the range variable (for $x)
     * @param pos the position variable (at $n)
     * @param seq the sequence (in ...)
     */
    public ForClause(Variable var, Variable pos, AbstractExpression seq) {
        this.var = var;
        this.pos = pos;
        this.seq = seq;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder ();
        buf.append ("for ");
        buf.append (var.toString());
        if (pos != null) {
            buf.append (" at ");
            buf.append (pos.toString());
        }
        buf.append (" in ");
        buf.append (seq.toString());
        return buf.toString();
    }

}
