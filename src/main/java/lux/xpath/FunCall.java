package lux.xpath;

import lux.api.ValueType;

public class FunCall extends AbstractExpression {

    private final QName name;
    private final ValueType returnType;

    public FunCall (QName name, ValueType returnType, AbstractExpression ... arguments) {
        super (Type.FunctionCall);
        this.name = name;
        this.subs = arguments;
        this.returnType = returnType;
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

    public ValueType getReturnType() {
        return returnType;
    }

}
