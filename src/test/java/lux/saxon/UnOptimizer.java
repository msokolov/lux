package lux.saxon;

import lux.index.IndexConfiguration;
import lux.query.SpanMatchAll;
import lux.xml.QName;
import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitorBase;
import lux.xpath.FunCall;
import lux.xpath.LiteralExpression;
import lux.xquery.XQuery;

public class UnOptimizer extends ExpressionVisitorBase {

    private static final QName luxSearchQName = new QName (FunCall.LUX_NAMESPACE, "search", "lux");
    private final IndexConfiguration indexConfig;
    
    public UnOptimizer (IndexConfiguration indexConfig) {
        this.indexConfig = indexConfig;
    }
    
    public AbstractExpression unoptimize (AbstractExpression aex) {
        aex.accept(this);
        return aex;
    }
    
    @Override
    public AbstractExpression visit(FunCall func) {
        if (func.getName().equals(luxSearchQName)) {
            if (indexConfig.isOption (IndexConfiguration.INDEX_PATHS)) {
                func.getSubs()[0] = SpanMatchAll.getInstance().toXmlNode("lux_path");
            } else {
                func.getSubs()[0] = new LiteralExpression ("*:*");
            }
        }
        return func;
    }

    public XQuery unoptimize(XQuery xquery) {
        AbstractExpression body = unoptimize(xquery.getBody());
        return new XQuery (xquery.getDefaultElementNamespace(), xquery.getDefaultFunctionNamespace(), xquery.getDefaultCollation(),
                xquery.getModuleImports(), xquery.getNamespaceDeclarations(), xquery.getVariableDefinitions(), xquery.getFunctionDefinitions(),
                body, xquery.getBaseURI(), xquery.isPreserveNamespaces(), xquery.isInheritNamespaces(), xquery.isEmptyLeast());        
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
