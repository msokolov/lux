package lux.xquery;

import lux.ExpressionVisitor;
import lux.xpath.AbstractExpression;

public class DocumentConstructor extends AbstractExpression {
    
    public DocumentConstructor (AbstractExpression content) {
        super (Type.DocumentConstructor);
        subs = new AbstractExpression[] { content };
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ("document { ");
        subs[0].toString(buf);
        buf.append (" }");
    }

    @Override
    public int getPrecedence () {
        return 100;
    }
}
