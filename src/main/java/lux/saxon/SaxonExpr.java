package lux.saxon;

import java.util.Map;

import lux.api.QueryContext;
import lux.api.ResultSet;
import lux.xpath.AbstractExpression;
import lux.xpath.QName;
import lux.xquery.XQuery;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;

/*
 * Wraps an abstract Lux expression as well as an executable Saxon expression;
 * either an XQuery or an XPath expression.
 */
public class SaxonExpr implements lux.api.Expression {

    private AbstractExpression xpath;
    private XPathExecutable xpathExec;
    
    private XQuery xquery;
    private XQueryExecutable xqueryExec;
    
    /**
     * Construct a SaxonExpr from an xpath expression 
     * @param xpath
     */
    public SaxonExpr (XPathExecutable xpathExec, AbstractExpression xpath) {
        this.xpathExec = xpathExec;
        this.xpath = xpath;
    }
    
    /**
     * Construct a SaxonExpr from an xquery expression 
     * @param xpathExec
     */
    public SaxonExpr (XQueryExecutable xqueryExec, XQuery xquery) {
        this.xqueryExec = xqueryExec;
        this.xquery = xquery;
    }

    /**
     * @return a Lux abstract XQuery
     */
    public XQuery getXQuery() {
        return xquery;
    }

    /**
     * @return the optimized XPath expression in lux's abstract representation.  If this is modeling an XQuery module,
     * the expression for the body is returned.
     */
    public AbstractExpression getXPath() {
        if (xpath != null) {
            return xpath;
        }
        return xquery.getBody();
    }
    
    public ResultSet<?> evaluate(QueryContext context) throws SaxonApiException {
        if (xpathExec != null) {
           return new XdmResultSet (evaluateXPath(context));
        }
        if (xqueryExec != null) {
            return new XdmResultSet (evaluateXQuery(context));
        }
        // TODO: throw an exception
        return null;
    }
    
    private XdmValue evaluateXQuery (QueryContext context) throws SaxonApiException {
        XQueryEvaluator eval = xqueryExec.load();
        if (context != null) {
            eval.setContextItem((XdmItem) context.getContextItem());
            if (context.getVariableBindings() != null) {
                for (Map.Entry<QName, Object> binding : context.getVariableBindings().entrySet()) {
                    net.sf.saxon.s9api.QName saxonQName = new net.sf.saxon.s9api.QName(binding.getKey());
                    eval.setExternalVariable(saxonQName, (XdmValue) binding.getValue());
                }
            }
        }
        return eval.evaluate();
    }
    
    private XdmValue evaluateXPath (QueryContext context) throws SaxonApiException {
        XPathSelector eval = xpathExec.load();
        if (context != null)
            eval.setContextItem((XdmItem) context.getContextItem());
        return eval.evaluate();
    }
    
    public String toString () {
        if (xquery != null) {
            return xquery.toString();
        }
        return xpath.toString();
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
