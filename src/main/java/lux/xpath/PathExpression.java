package lux.xpath;

/**
 * A path expression represents two expressions joined with a "/"
 * @author sokolov
 *
 */
public class PathExpression extends AbstractExpression {
    
    public PathExpression (AbstractExpression lhs, AbstractExpression rhs) {
        super (Type.PathExpression);
        subs = new AbstractExpression[2];
        subs[0] = lhs;
        subs[1] = rhs;
    }
    
    public final AbstractExpression getRHS() {
        return subs[1];
    }
    
    public final AbstractExpression getLHS() {
        return subs[0];
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

    public AbstractExpression accept(ExpressionVisitor visitor) {
        acceptSubs(visitor);
        return visitor.visit(this);
    }

    /**
     * @return the expression left after removing the left-most sub-expression.
     */
    public AbstractExpression getTail() {
        AbstractExpression left = subs[0].getTail();
        if (left == null) {
            return subs[1];
        }
        return new PathExpression (left, subs[1]);
    }

}
