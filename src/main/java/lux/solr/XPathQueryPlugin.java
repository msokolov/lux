package lux.solr;


import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;

public class XPathQueryPlugin extends QParserPlugin {
    
    private SolrParams config;
    private String defXPathImpl;
    
    // FIXME: this 'engine' parameter is kind of bogus since it can conflict with the 
    // config in XPathSearchComponent - they should both be controlled by the same setting
    public void init(@SuppressWarnings("rawtypes") NamedList args) {
        if (args != null) {
            config = SolrParams.toSolrParams(args);
            defXPathImpl = config.get("engine", "jaxen");
        }
    }

    @Override
    public QParser createParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
        String xpathImpl = params.get("engine", defXPathImpl);                
        if ("jaxen".equals(xpathImpl))
            return new JaxenParser (this, qstr, localParams, params, config, req);
        if ("saxon".equals(xpathImpl))
            return new SaxonParser (this, qstr, localParams, params, config, req);
        throw new SolrException (ErrorCode.BAD_REQUEST, "Unknown xpath parser: " + xpathImpl);
    }


}
