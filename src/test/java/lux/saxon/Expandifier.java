package lux.saxon;

import lux.xml.ValueType;
import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitorBase;
import lux.xpath.FunCall;
import lux.xpath.Root;
import lux.xquery.XQuery;

/*
 * prefix absolute expressions with collection()
 */
public class Expandifier extends ExpressionVisitorBase {

    public AbstractExpression expandify (AbstractExpression aex) {
        return aex.accept(this);
    }
    
    @Override
    public AbstractExpression visit(Root root) {
        return new FunCall(FunCall.FN_COLLECTION, ValueType.DOCUMENT);
    }

    public XQuery expandify(XQuery xquery) {
        AbstractExpression body = expandify(xquery.getBody());
        return new XQuery (xquery.getDefaultElementNamespace(), xquery.getDefaultFunctionNamespace(), xquery.getDefaultCollation(),
                xquery.getModuleImports(), xquery.getNamespaceDeclarations(), xquery.getVariableDefinitions(), xquery.getFunctionDefinitions(),
                body, xquery.getBaseURI(), xquery.isPreserveNamespaces(), xquery.isInheritNamespaces(), xquery.isEmptyLeast());        
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
