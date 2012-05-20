package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;
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
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder ();
        buf.append ("attribute ");
        buf.append (name.toString());
        buf.append (" { ");
        buf.append (content.toString());
        buf.append (" }");
        return buf.toString();
     }

}
