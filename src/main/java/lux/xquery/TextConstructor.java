package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;

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
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append ("text { ");
        buf.append (content.toString());
        buf.append (" } ");
        return buf.toString();
    }

}
