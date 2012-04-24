package lux.xpath;

/**
 * represents numeric literal predicates like [1]; last-predicates like [last()] and
 * calls to the subsequence(expr,integer,integer) function.
 * 
 * @author sokolov
 *
 */
public class Subsequence extends AbstractExpression {

    public Subsequence (AbstractExpression sequence, AbstractExpression start, AbstractExpression length) {
        super (Type.Subsequence);
        subs = new AbstractExpression[] { sequence, start, length };
    }
    
    public Subsequence (AbstractExpression sequence, AbstractExpression start) {
        super (Type.Subsequence);
        subs = new AbstractExpression[] { sequence, start };
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        acceptSubs(visitor);
        return visitor.visit(this);
    }
    
    public AbstractExpression getSequence() {
        return subs[0];
    }

    public AbstractExpression getStartExpr () {
        return subs[1];
    }

    public AbstractExpression getLengthExpr () {
        return subs.length > 2 ? subs[2] : null;
    }
    
    @Override
    public boolean isAbsolute () {
        return getSequence().isAbsolute();
    }

    @Override
    public String toString() {
        if (getLengthExpr() == null) {
            return "subsequence(" + getSequence().toString() + ',' + getStartExpr().toString() + ')';
        }
        if (getLengthExpr().equals(LiteralExpression.ONE)) {
            if (getSequence().getSubs() != null) {
                return '(' + getSequence().toString() + ")[" + getStartExpr().toString() + ']';
            }
            return getSequence().toString() + '[' + getStartExpr().toString() + ']';
        }
        return "subsequence(" + getSequence().toString() + ',' + getStartExpr().toString() + ',' +
                getLengthExpr().toString() + ')';
    }

}
