package lux.xquery;

import lux.ExpressionVisitor;
import lux.xpath.AbstractExpression;

/**
 * represents xquery conditionals (if, then, else)
 */
public class Conditional extends AbstractExpression {

    private final AbstractExpression condition;
    private final AbstractExpression trueAction;
    private final AbstractExpression falseAction;
    
    public Conditional (AbstractExpression condition, AbstractExpression trueAction, AbstractExpression falseAction) {
        super (Type.Conditional);
        this.condition = condition;
        this.trueAction = trueAction;
        this.falseAction = falseAction;
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        acceptSubs(visitor);
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ("if (");
        condition.toString(buf);
        buf.append (") then (");
        trueAction.toString(buf);
        if (falseAction != null) {
            buf.append(") else (");
            falseAction.toString(buf);
        }
        buf.append (")");
    }

}
