/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.saxon;

import lux.api.ResultSet;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmItem;

public class SaxonExpr implements lux.api.Expression {

    private XPathExecutable xpath;
    private XQueryExecutable xquery;
    
    /**
     * Construct a SaxonExpr from an xpath expression 
     * @param xpath
     */
    public SaxonExpr (XPathExecutable xpath) {
        this.xpath = xpath;
    }
    
    /**
     * Construct a SaxonExpr from an xquery expression 
     * @param xpath
     */
    public SaxonExpr (XQueryExecutable xquery) {
        this.xquery = xquery;
    }

    /**
     * @return the expression in Saxon's internal form, which provides a sufficiently detailed api
     * that we can inspect the entire expression tree.
     */
    public Expression getSaxonInternalExpression() {
        if (xpath != null) {
            return xpath.getUnderlyingExpression().getInternalExpression();
        }
        return xquery.getUnderlyingCompiledQuery().getExpression();
    }
    
    public ResultSet<?> evaluate(XdmItem contextItem) throws SaxonApiException {
        if (xpath != null) {
            XPathSelector eval = xpath.load();
            if (contextItem != null)
                eval.setContextItem(contextItem);
            return new XdmResultSet (eval.evaluate());
        }
        XQueryEvaluator eval = xquery.load();
        if (contextItem != null)
            eval.setContextItem(contextItem);
        return new XdmResultSet (eval.evaluate());
    }
    
    public String toString () {
        return getSaxonInternalExpression().toString();
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
