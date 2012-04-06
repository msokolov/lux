package lux.xpath;

public class PathExpression extends AbstractExpression {
    
    private AbstractExpression lhs;
    private AbstractExpression rhs;
    
    public PathExpression (AbstractExpression lhs, AbstractExpression rhs) {
        super (Type.PathExpression);
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @Override
    public String toString() {
        return lhs.toString() + '/' + rhs.toString();
    }

}
