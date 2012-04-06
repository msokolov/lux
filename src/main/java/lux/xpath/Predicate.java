package lux.xpath;

public class Predicate extends AbstractExpression {
    
    private final AbstractExpression base;
    private final AbstractExpression filter;
    
    public Predicate (AbstractExpression base, AbstractExpression filter) {
        super (Type.Predicate);
        this.base = base;
        this.filter = filter;
    }
    
    public String toString () {
        return base.toString() + '[' + filter.toString() + ']';
    }
    
    public AbstractExpression getBase() {
        return base;
    }

    public AbstractExpression getFilter() {
        return filter;
    }

}
