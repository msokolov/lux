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
    
    public static final String LUX_NAMESPACE = "lux";
    public static final QName LUX_SEARCH = new QName (LUX_NAMESPACE, "search", "lux");
    public static final QName LUX_COUNT = new QName (LUX_NAMESPACE, "count", "lux");
    public static final QName LUX_EXISTS = new QName (LUX_NAMESPACE, "exists", "lux");
    public static final QName LUX_ROOT = new QName (LUX_NAMESPACE, "root", "lux");
    
    public static final String FN_NAMESPACE = "http://www.w3.org/2005/xpath-functions";
    public static final QName FN_ROOT = new QName (FN_NAMESPACE, "root", "");
    public static final QName FN_LAST = new QName (FN_NAMESPACE, "last", "");
    public static final QName FN_SUBSEQUENCE = new QName (FN_NAMESPACE, "subsequence", "");
    public static final QName FN_COUNT = new QName (FN_NAMESPACE, "count", "");
    public static final QName FN_EXISTS = new QName (FN_NAMESPACE, "exists", "");
    public static final QName FN_NOT = new QName (FN_NAMESPACE, "not", "");
    public static final QName FN_EMPTY = new QName (FN_NAMESPACE, "empty", "");

    // represent last() in Subsequence(foo, last()); ie foo[last()].
    public static final FunCall LastExpression = new FunCall (FN_LAST, ValueType.VALUE);    
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        return visitor.visit(this);
    }

    public ValueType getReturnType() {
        return returnType;
    }
    
    @Override
    public boolean isDocumentOrdered () {
        return !returnType.isAtomic && 
                super.isDocumentOrdered() &&
                (name.getNamespaceURI().equals(LUX_NAMESPACE) || 
                 (name.getNamespaceURI().equals(FN_NAMESPACE) &&
                         ! name.getLocalPart().equals ("reverse") &&
                         ! name.getLocalPart().equals("unordered")));
    }

}
