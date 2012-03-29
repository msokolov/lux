package lux.saxon;

import lux.api.Context;
import lux.api.QueryContext;
import net.sf.saxon.s9api.XdmItem;

import org.apache.lucene.search.IndexSearcher;

public class SaxonContext extends QueryContext implements Context {

    private XdmItem contextItem;
    
    public SaxonContext (IndexSearcher searcher) {
       super (searcher);
    }

    public String getXmlFieldName() {
        return "xml_text";
    }
    
    public void setContextItem (XdmItem item) {
        contextItem = item;
    }
    
    public XdmItem getContextItem () {
        return contextItem;
    }

}
