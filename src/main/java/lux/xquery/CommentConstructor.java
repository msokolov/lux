package lux.xquery;

import lux.ExpressionVisitor;
import lux.xpath.AbstractExpression;

public class CommentConstructor extends AbstractExpression {

    private final AbstractExpression content;
    
    public CommentConstructor (AbstractExpression abstractExpression) {
        super (Type.Comment);
        this.content = abstractExpression;
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append("comment { ");
        content.toString(buf);
        buf.append(" }");
    }

    @Override
    public int getPrecedence () {
        return 100;
    }

}
