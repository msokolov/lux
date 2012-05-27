package lux.saxon;

import lux.api.ResultSet;
import lux.xpath.AbstractExpression;
import lux.xquery.XQuery;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmItem;

/*
 * TODO:
 *  
 *  get/set/clear external variable bindings
 *  get/set/clear ContextItem
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
     * @return the XPath expression in lux's abstract representation.  If this is modeling an XQuery module,
     * the expression for the body is returned.
     */
    public AbstractExpression getXPath() {
        if (xpath != null) {
            return xpath;
        }
        return xquery.getBody();
    }

    
    public ResultSet<?> evaluate(XdmItem contextItem) throws SaxonApiException {
        if (xpathExec != null) {
            XPathSelector eval = xpathExec.load();
            if (contextItem != null)
                eval.setContextItem(contextItem);
            return new XdmResultSet (eval.evaluate());
        }
        XQueryEvaluator eval = xqueryExec.load();
        if (contextItem != null)
            eval.setContextItem(contextItem);
        return new XdmResultSet (eval.evaluate());
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
