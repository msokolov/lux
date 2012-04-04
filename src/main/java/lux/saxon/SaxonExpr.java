package lux.saxon;

import lux.XPathQuery;
import lux.api.ValueType;
import net.sf.saxon.Configuration;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.type.ItemType;
import net.sf.saxon.type.Type;

public class SaxonExpr implements lux.api.Expression {

    private XPathExecutable xpath;
    private XPathQuery query;
    
    /**
     * Construct a SaxonExpr from an xpath expression using the LuxOptimizer, which is stored away in the Saxon.
     * @param xpath
     * @param saxon
     */
    public SaxonExpr (XPathExecutable xpath, Saxon saxon) {
        this.xpath = xpath;
        query = saxon.getConfig().getOptimizer().queryFor (this);
        ValueType valueType = getValueType(xpath.getUnderlyingExpression().getInternalExpression(), saxon.getConfig());
        if (valueType != null)
            query.restrictType(valueType);
    }
    
    /**
     * Construct a SaxonExpr from the given xpath and query.  If these are not compatible, incorrect
     * evaluating this may return incorrect results.  This method is for testing purposes only.
     * @param xpath
     * @param query
     */
    public SaxonExpr (XPathExecutable xpath, XPathQuery query) {
        this.xpath = xpath;
        this.query = query;
    }
    
    public static ValueType getValueType (Expression expr, Configuration config) {
        ValueType valueType = null;
        ItemType itemType = expr.getItemType(config.getTypeHierarchy());
        if (itemType.isPlainType()) {
            valueType = ValueType.ATOMIC;
        } else {
            switch (itemType.getPrimitiveType()) {        
            case Type.DOCUMENT: valueType = ValueType.DOCUMENT; break;
            case Type.ELEMENT: valueType = ValueType.ELEMENT; break;
            case Type.TEXT: valueType = ValueType.TEXT; break;
            case Type.ATTRIBUTE: valueType = ValueType.ATTRIBUTE; break;
            case Type.NODE: valueType = ValueType.NODE; break;
            case Type.COMMENT: valueType = ValueType.NODE; break;
            case Type.PROCESSING_INSTRUCTION: valueType = ValueType.NODE; break;
            case Type.ITEM: valueType = ValueType.VALUE; break;
            case Type.NAMESPACE: valueType = ValueType.NODE; break;
            }
        }
        return valueType;
    }

    public XPathExecutable getXPathExecutable() {
        return xpath;
    }
    
    public XPathQuery getXPathQuery () {
        return query;
    }

    /**
     * @return the Lucene query, as a string.  This is usually in a form that can be searched when 
     * parsed using the standard Lucene query parser, but in some cases may not be.  @see Query.toString()
     */
    public String getSearchQuery() {
        return query.toString();
    }

    public XdmResultSet evaluate(XdmItem contextItem) throws SaxonApiException {
        XPathSelector eval = xpath.load();
        if (contextItem != null)
            eval.setContextItem(contextItem);
        return new XdmResultSet (eval.evaluate());
    }

}
