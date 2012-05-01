package lux.xpath;

public class Let extends AbstractExpression {

    private QName name;
    
    public Let (QName name, AbstractExpression assignment, AbstractExpression returnExp) {
        super (Type.Let);
        subs = new AbstractExpression [] { assignment, returnExp };
        this.name = name;
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return "let $" + name.toString() + " := " + getAssignment().toString() 
                + " return " + getReturn().toString();
    }
    
    public QName getName () {
        return name;
    }
    
    public AbstractExpression getAssignment () {
        return subs[0];
    }
    
    public AbstractExpression getReturn () {
        return subs[1];
    }
    
    @Override
    public boolean isDocumentOrdered () {
        return getReturn().isDocumentOrdered();
    }

}
