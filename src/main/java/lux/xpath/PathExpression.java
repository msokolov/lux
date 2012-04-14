package lux.xpath;

public class PathExpression extends AbstractExpression {
    
    // TODO: specialize to PathStep args?
    public PathExpression (AbstractExpression lhs, AbstractExpression rhs) {
        super (Type.PathExpression);
        subs = new AbstractExpression[2];
        subs[0] = lhs;
        subs[1] = rhs;
    }

    @Override
    public String toString() {
        if (subs[0] instanceof Root) {
            return '/' + subs[1].toString();
        }
        return subs[0].toString() + '/' + subs[1].toString();
    }
    
    /**
     * Whenever we see a new absolute context (/, collection(), search()), its dependent 
     * expressions are a possible target for optimizarion.
     * @return whether the lhs of this path is an expression returning Documents.
     */
    public boolean isAbsolute() {
       return subs[0].isAbsolute();
    }

    public void accept(ExpressionVisitor visitor) {
        acceptSubs(visitor);
        visitor.visit(this);
    }
}
