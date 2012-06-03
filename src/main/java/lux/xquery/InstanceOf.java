package lux.xquery;

import lux.ExpressionVisitor;
import lux.xpath.AbstractExpression;

public class InstanceOf extends AbstractExpression {

    private final String typeName;
    
    public InstanceOf (String typeName, AbstractExpression valueExpr) {
        super (Type.InstanceOf);
        this.typeName = typeName;
        subs = new AbstractExpression [] { valueExpr };
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        subs[0].toString(buf);
        buf.append (" instance of ").append(typeName);
    }

    @Override
    public int getPrecedence() {
        return 12;
    }

}
