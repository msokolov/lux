package lux.xpath;

public class FunCall extends AbstractExpression {

    private QName name;
    
    private AbstractExpression[] arguments;
    
    public FunCall (QName name, AbstractExpression ... arguments) {
        super (Type.FunctionCall);
        this.name = name;
        this.arguments = arguments;
    }
    
    @Override
    public String toString() {
        return name.toString() + Sequence.seqAsString(arguments);
    }

}
