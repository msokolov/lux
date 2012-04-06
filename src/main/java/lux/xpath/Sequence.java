package lux.xpath;

public class Sequence extends AbstractExpression {

    private AbstractExpression[] contents;
    
    public Sequence (AbstractExpression ... contents) {
        super (Type.Sequence);
        this.contents = contents;
    }
    
    @Override
    public String toString() {
        return seqAsString(contents);
    }
    
    static final String seqAsString (AbstractExpression ... contents) {
        StringBuilder buf = new StringBuilder ();
        buf.append('(');
        boolean first = true;
        for (AbstractExpression arg : contents) {
            if (first) {
                first = false;
            } else {
                buf.append(',');
            }
            buf.append (arg);
        }
        buf.append (')');
        return buf.toString();
    }

}
