package lux.saxon;

import lux.ResultList;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;

public class SaxonExpr implements lux.api.Expression {

    private XPathExecutable xpath;
    
    /**
     * Construct a SaxonExpr from an xpath expression using the Saxon's SaxonTranslator, 
     * which is stored away in the Saxon.
     * @param xpath
     * @param saxon
     */
    public SaxonExpr (XPathExecutable xpath) {
        this.xpath = xpath;
    }

    public XPathExecutable getXPathExecutable() {
        return xpath;
    }
    
    public ResultList<?> evaluate(XdmItem contextItem) throws SaxonApiException {
        XPathSelector eval = xpath.load();
        if (contextItem != null)
            eval.setContextItem(contextItem);
        return new XdmResultSet (eval.evaluate());
    }

}
