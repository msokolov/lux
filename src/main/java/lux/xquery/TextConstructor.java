package lux.xquery;

import lux.ExpressionVisitor;
import lux.xpath.AbstractExpression;

public class TextConstructor extends AbstractExpression {
    
    public TextConstructor (AbstractExpression expression) {
        super (Type.Text);
        subs = new AbstractExpression [] { expression };
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ("text { ");
        getContent().toString(buf);
        buf.append (" } ");
    }

    private AbstractExpression getContent() {
        return subs[0];
    }

    @Override
    public int getPrecedence () {
        return 100;
    }
}
