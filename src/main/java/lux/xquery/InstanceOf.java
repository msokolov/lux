package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;

public class InstanceOf extends AbstractExpression {

    private final String typeName;
    
    public InstanceOf (String typeName, AbstractExpression valueExpr) {
        super (Type.INSTANCE_OF);
        this.typeName = typeName;
        subs = new AbstractExpression [] { valueExpr };
    }
    
    @Override
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
    
    /**
     * @return the binding context of the base expression
     */
    @Override
    public VariableContext getBindingContext () {
        return subs[0].getBindingContext();
    }
}
