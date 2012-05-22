package lux.xquery;

import lux.ExpressionVisitor;
import lux.xpath.AbstractExpression;
import lux.xpath.QName;

public class AttributeConstructor extends AbstractExpression {

    private final QName name;
    private final AbstractExpression content;
    
    public AttributeConstructor(QName name, AbstractExpression content) {
        super(Type.Attribute);
        this.name = name;
        this.content = content;
    }

    public AbstractExpression accept(ExpressionVisitor visitor) {
        acceptSubs(visitor);
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ("attribute ");
        name.toString (buf);
        buf.append (" { ");
        content.toString (buf);
        buf.append (" }");
     }

}
