/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xpath;

import lux.ExpressionVisitor;
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
    public void toString(StringBuilder buf) {
        buf.append (name);
        buf.append ('(');
        if (subs.length > 0) {
            buf.append (subs[0]);
        }
        for (int i = 1; i < subs.length; i++) {
            buf.append (',');
            buf.append (subs[i]);
        }
        buf.append (')');
    }
    
    public QName getName() {
        return name;
    }
    
    public static final String LUX_NAMESPACE = "lux";
    public static final QName LUX_SEARCH = new QName (LUX_NAMESPACE, "search", "lux");
    public static final QName LUX_COUNT = new QName (LUX_NAMESPACE, "count", "lux");
    public static final QName LUX_EXISTS = new QName (LUX_NAMESPACE, "exists", "lux");
    public static final QName LUX_ROOT = new QName (LUX_NAMESPACE, "root", "lux");
    
    public static final String FN_NAMESPACE = "http://www.w3.org/2005/xpath-functions";
    public static final QName FN_ROOT = new QName (FN_NAMESPACE, "root", "fn");
    public static final QName FN_LAST = new QName (FN_NAMESPACE, "last", "fn");
    public static final QName FN_SUBSEQUENCE = new QName (FN_NAMESPACE, "subsequence", "fn");
    public static final QName FN_COUNT = new QName (FN_NAMESPACE, "count", "fn");
    public static final QName FN_EXISTS = new QName (FN_NAMESPACE, "exists", "fn");
    public static final QName FN_NOT = new QName (FN_NAMESPACE, "not", "fn");
    public static final QName FN_EMPTY = new QName (FN_NAMESPACE, "empty", "fn");
    public static final QName FN_COLLECTION = new QName (FN_NAMESPACE, "collection", "fn");

    public static final String LOCAL_NAMESPACE = "http://www.w3.org/2005/xquery-local-functions";
    
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
    
    public boolean isAbsolute () {
        if (name.equals(FN_COLLECTION)) {
            return true;
        }
        return false;
    }
    
    /** 
     * replace collection() with the search function call
     * @param search the search function call to use
     * @return the search function call if this is collection(), otherwise return this
     */
    public AbstractExpression replaceRoot(FunCall search) {        
        if (name.equals(FN_COLLECTION)) {
            return search;
        }
        return this;
    }

}
