package lux.xpath;

public class FunCall extends AbstractExpression {

    private QName name;

    public FunCall (QName name, AbstractExpression ... arguments) {
        super (Type.FunctionCall);
        this.name = name;
        this.subs = arguments;
    }
    
    @Override
    public String toString() {
        return name.toString() + Sequence.seqAsString(",", subs);
    }
    
    // TODO: move this elsewhere?
    public static QName luxSearchQName = new QName ("net.lux", "lux", "search");

    public void accept(Visitor<AbstractExpression> visitor) {
        visitor.visit(this);
    }
}
