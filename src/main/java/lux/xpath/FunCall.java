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
    public static final String FN_NAMESPACE = "http://www.w3.org/2005/xpath-functions";
    public static final String LUX_NAMESPACE = "lux";
    public static final QName luxSearchQName = new QName (LUX_NAMESPACE, "search", "lux");
    public static QName luxCountQName = new QName (LUX_NAMESPACE, "count", "lux");
    public static final QName luxExistsQName = new QName (LUX_NAMESPACE, "exists", "lux");
    public static final QName notQName = new QName (FN_NAMESPACE, "not", "");
    public static final QName rootQName = new QName (FN_NAMESPACE, "root", "");
    public static final QName lastQName = new QName (FN_NAMESPACE, "last", "");
    public static final QName subsequenceQName = new QName (FN_NAMESPACE, "subsequence", "");
    public static final QName countQName = new QName (FN_NAMESPACE, "count", "");

    // represent last() in Subsequence(foo, last()); ie foo[last()].
    public static final FunCall LastExpression = new FunCall (lastQName, ValueType.VALUE);    
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        return visitor.visit(this);
    }

    public ValueType getReturnType() {
        return returnType;
    }

}
