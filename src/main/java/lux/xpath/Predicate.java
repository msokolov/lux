package lux.xpath;

public class Predicate extends AbstractExpression {
    
    public Predicate (AbstractExpression base, AbstractExpression filter) {
        super (Type.Predicate);
        subs = new AbstractExpression[] { base, filter };
    }
    
    public String toString () {
        return subs[0].toString() + '[' + subs[1].toString() + ']';
    }
    
    public AbstractExpression getBase() {
        return subs[0];
    }

    public AbstractExpression getFilter() {
        return subs[1];
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        return visitor.visit(this);
    }
    
    public boolean isAbsolute () {
        return subs[0].isAbsolute();
    }

}
