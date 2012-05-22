package lux.xquery;

import lux.ExpressionVisitor;
import lux.xpath.AbstractExpression;
import lux.xpath.QName;

public class AttributeConstructor extends AbstractExpression {

    private final QName name;
    
    public AttributeConstructor(QName name, AbstractExpression content) {
        super(Type.Attribute);
        this.name = name;
        subs = new AbstractExpression[] { content };
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
        getContent().toString (buf);
        buf.append (" }");
     }
    
    public final AbstractExpression getContent () {
        return subs[0];
    }

}
