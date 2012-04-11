package lux.xpath;

public class UnaryMinus extends AbstractExpression {

    public UnaryMinus (AbstractExpression operand) {
        super (Type.UnaryMinus);
        subs = new AbstractExpression[] { operand };        
    }
    
    public AbstractExpression getOperand () {
        return subs[0];
    }
    
    @Override
    public String toString() {
        return '-' + subs[0].toString();
    }

    public void accept(Visitor<AbstractExpression> visitor) {
        visitor.visit(this);
    }
}
