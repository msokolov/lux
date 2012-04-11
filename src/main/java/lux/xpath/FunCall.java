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
    
    public QName getQName() {
        return name;
    }
    
    // TODO: move this elsewhere?
    public static String FN_NAMESPACE = "http://www.w3.org/2005/xpath-functions";
    public static QName luxSearchQName = new QName ("net.lux", "lux", "search");
    public static QName notQName = new QName (FN_NAMESPACE, "", "not");
    
    public void accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        visitor.visit(this);
    }
}
