package lux.xpath;

public class Variable extends AbstractExpression {
    
    private QName name;
    
    public Variable (QName qname) {
        super (Type.Variable);
        name = qname;
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return '$' + name.toString();
    }
    
    public QName getQName() {
        return name;
    }
}
