package lux.xpath;

public class Sequence extends AbstractExpression {
    
    public Sequence (AbstractExpression ... contents) {
        super (Type.Sequence);
        subs = contents;
    }
    
    @Override
    public String toString() {
        return seqAsString(",", subs);
    }
    
    static final String seqAsString (String separator, AbstractExpression ... contents) {
        StringBuilder buf = new StringBuilder ();
        buf.append('(');
        appendSeq(buf, contents, separator);
        buf.append (')');
        return buf.toString();
    }

    static boolean appendSeq(StringBuilder buf, AbstractExpression[] contents, String separator) {
        boolean first = true;
        for (AbstractExpression arg : contents) {
            if (first) {
                first = false;
            } else {
                buf.append(separator);
            }
            if (arg.getType() == Type.Sequence) {
                appendSeq (buf, arg.getSubs(), separator);
            } else {
                buf.append (arg);
            }
        }
        return first;
    }

    public void accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        visitor.visit(this);
    }
}
