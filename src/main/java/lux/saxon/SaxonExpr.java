/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.saxon;

import lux.api.ResultSet;
import lux.xpath.AbstractExpression;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;

public class SaxonExpr implements lux.api.Expression {

    private XPathExecutable xpath;
    private AbstractExpression aex;
    
    /**
     * Construct a SaxonExpr from an xpath expression and a corresponding AbstractExpression,
     * stored for testing convenience 
     * @param xpath
     * @param saxon
     */
    public SaxonExpr (XPathExecutable xpath, AbstractExpression expr) {
        this.xpath = xpath;
        this.aex = expr;
    }

    public XPathExecutable getXPathExecutable() {
        return xpath;
    }
    
    public ResultSet<?> evaluate(XdmItem contextItem) throws SaxonApiException {
        XPathSelector eval = xpath.load();
        if (contextItem != null)
            eval.setContextItem(contextItem);
        return new XdmResultSet (eval.evaluate());
    }

    public AbstractExpression getExpression() {
        return aex;
    }
}
