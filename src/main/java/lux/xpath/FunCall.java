package lux.xpath;

import lux.xml.QName;
import lux.xml.ValueType;

public class FunCall extends AbstractExpression {

    public static final String LUX_NAMESPACE = lux.Evaluator.LUX_NAMESPACE;
    
    private final QName name;
    private final ValueType returnType;

    public FunCall (QName name, ValueType returnType, AbstractExpression ... arguments) {
        super (Type.FUNCTION_CALL);
        this.name = name;
        this.subs = arguments;
        this.returnType = returnType;
    }
    
    @Override
    public void toString(StringBuilder buf) {
        buf.append (name);
        buf.append ('(');
        if (subs.length == 1) {
            buf.append (subs[0]);
        } 
        else if (subs.length > 1) {
            subs[0].toString(buf);            
        }
        for (int i = 1; i < subs.length; i++) {
            buf.append (',');
            subs[i].toString(buf);
        }
        buf.append (')');
    }
    
    public QName getName() {
        return name;
    }
    
    /**
     * @return 100; the outer precedence.
     */
    @Override public int getPrecedence () {
        return 100;
    }

    public static final QName LUX_SEARCH = new QName (LUX_NAMESPACE, "search", "lux");
    public static final QName LUX_COUNT = new QName (LUX_NAMESPACE, "count", "lux");
    public static final QName LUX_EXISTS = new QName (LUX_NAMESPACE, "exists", "lux");
    public static final QName LUX_FIELD_VALUES = new QName (LUX_NAMESPACE, "field-values", "lux");
    
    public static final String FN_NAMESPACE = "http://www.w3.org/2005/xpath-functions";
    public static final QName FN_ROOT = new QName (FN_NAMESPACE, "root", "fn");
    public static final QName FN_LAST = new QName (FN_NAMESPACE, "last", "fn");
    public static final QName FN_DATA = new QName (FN_NAMESPACE, "data", "fn");
    public static final QName FN_SUBSEQUENCE = new QName (FN_NAMESPACE, "subsequence", "fn");
    public static final QName FN_COUNT = new QName (FN_NAMESPACE, "count", "fn");
    public static final QName FN_EXISTS = new QName (FN_NAMESPACE, "exists", "fn");
    public static final QName FN_NOT = new QName (FN_NAMESPACE, "not", "fn");
    public static final QName FN_EMPTY = new QName (FN_NAMESPACE, "empty", "fn");
    public static final QName FN_COLLECTION = new QName (FN_NAMESPACE, "collection", "fn");
    public static final QName FN_STRING_JOIN = new QName (FN_NAMESPACE, "string-join", "fn");
    public static final QName FN_CONTAINS = new QName(FN_NAMESPACE, "contains", "fn");

    public static final String LOCAL_NAMESPACE = "http://www.w3.org/2005/xquery-local-functions";
    public static final String XS_NAMESPACE = "http://www.w3.org/2001/XMLSchema";    
    
    // represent last() in Subsequence(foo, last()); ie foo[last()].
    public static final FunCall LastExpression = new FunCall (FN_LAST, ValueType.VALUE);
    
    @Override
    public AbstractExpression accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        return visitor.visit(this);
    }

    public ValueType getReturnType() {
        return returnType;
    }
    
    @Override
    public boolean isDocumentOrdered () {
        if (returnType.isAtomic) {
            return false;
        }
        if (name.getNamespaceURI().equals(LUX_NAMESPACE)) {
            if (name.getLocalPart().equals("search"))
                return true;
        }
        if (name.getNamespaceURI().equals(FN_NAMESPACE)) {
            if (name.getLocalPart().equals ("reverse") || name.getLocalPart().equals("unordered")) {
                return false;
            }
            if (name.getLocalPart().equals("root")) {
                return false;
            }
            return super.isDocumentOrdered();
        }
        return false;
    }

    /**
     * @return for "transparent" functions that return their argument, like
     * data() and typecasts, the argument's rightmost subexpression (last
     * context step) is returned.  For other functions, the function
     * expression itself is returned.
     */
    @Override
    public AbstractExpression getLastContextStep () {
        if (name.getNamespaceURI().equals(XS_NAMESPACE) ||
                (name.getNamespaceURI().equals(FN_NAMESPACE) && 
                        name.getLocalPart().equals("data"))) {
            return subs[0].getLastContextStep();
        }
        return this;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
