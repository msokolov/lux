package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;

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
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append ("if (");
        buf.append (condition.toString());
        buf.append (") then (");
        buf.append (trueAction.toString());
        if (falseAction != null) {
            buf.append(") else (");
            buf.append(falseAction.toString());
        }
        buf.append (")");
        return buf.toString();
    }

}
