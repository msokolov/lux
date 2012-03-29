package lux.solr;

import lux.XPathQuery;
import lux.jaxen.LuXPathBasic;

import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QParser;
import org.jaxen.Context;
import org.jaxen.ContextSupport;
import org.jaxen.JaxenException;

public class JaxenParser extends QParser {
    
    private LuXPathBasic luXPath;
    private String eltField;
    private String attField;
    
    public JaxenParser(XPathQueryPlugin xPathQueryParserFactory, String qstr, SolrParams localParams, SolrParams params, SolrParams config, SolrQueryRequest req) {
        super(qstr, localParams, params, req);
        eltField = config.get("elt-name-field-name", "lux_elt_name_ms");
        attField = config.get("att-name-field-name", "lux_att_name_ms");
        // TODO: read namespace-aware; read namespace mapping
        // TODO: read xpath index config
        // TODO: pagination
        // TODO: variables from params/localParams?
        // TODO: namespace mappings from params/localParams?
        
    }

    @Override
    public Query parse() throws ParseException {
        Context context = new Context(new ContextSupport());
        try {
            luXPath = new LuXPathBasic(qstr);
            luXPath.setAttrQNameField(attField);
            luXPath.setElementQNameField(eltField);
            XPathQuery query = luXPath.getQuery (luXPath.getRootExpr(), context);
            query.setExpression(luXPath);
            return query;     
        } catch (JaxenException e) {
            throw new ParseException ("failed to parse xpath: " + qstr + "; " + e.getMessage());
        }
    }

}