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
    
    /*
    public AbstractExpression optimize () {
        // TODO: implement this for BinaryOperation, FunCall, Sequence, SetOperation and 
        // Unary 
        if (filter.isAbsolute()) {
            Query query = getQuery (filter);
            if (query != null) {
                FunCall search = new FunCall(FunCall.luxSearchQName, filter.optimize());            
                return new Predicate (base.optimize(), search);
            }
        }
        return new Predicate (base.optimize(), filter.optimize());
    }
*/
    public void accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        visitor.visit(this);
    }

}
