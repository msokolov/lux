package lux.xquery;

import lux.ExpressionVisitor;
import lux.xpath.AbstractExpression;

public class TextConstructor extends AbstractExpression {

    private AbstractExpression content;
    
    public TextConstructor (AbstractExpression expression) {
        super (Type.Text);
        this.content = expression;
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ("text { ");
        content.toString(buf);
        buf.append (" } ");
    }

}
