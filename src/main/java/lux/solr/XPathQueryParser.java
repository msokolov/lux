package lux.solr;

import lux.LuXPathBasic;
import lux.XPathQuery;

import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QParser;
import org.apache.solr.search.QParserPlugin;
import org.jaxen.Context;
import org.jaxen.ContextSupport;
import org.jaxen.JaxenException;

public class XPathQueryParser extends QParserPlugin {
    
    private String eltNameFieldName;
    private String attNameFieldName;
    //private String pathFieldName;
    
    public void init(@SuppressWarnings("rawtypes") NamedList args) {
        // TODO: refactor, copied from LuxProcessor
        if (args != null) {
            SolrParams params = SolrParams.toSolrParams(args);
            eltNameFieldName = params.get("elt-name-field-name", "lux_elt_name_ms");
            attNameFieldName = params.get("att-name-field-name", "lux_att_name_ms");
            //pathFieldName = params.get("path-field-name", "lux_path_ms");
            // TODO: read namespace-aware; read namespace mapping
            // TODO: read xpath index config
        }
    }

    @Override
    public QParser createParser(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
        return new XPathParserInstance (qstr, localParams, params, req);
    }
    
    public class XPathParserInstance extends QParser {
        
        private LuXPathBasic luXPath;
        private String parseError;
        
        public XPathParserInstance(String qstr, SolrParams localParams, SolrParams params, SolrQueryRequest req) {
            super(qstr, localParams, params, req);
            // TODO: pagination
            // TODO: variables from params/localParams?
            // TODO: namespace mappings from params/localParams?
            try {
                luXPath = new LuXPathBasic(qstr);
                luXPath.setAttrQNameField(attNameFieldName);
                luXPath.setElementQNameField(eltNameFieldName);
            } catch (JaxenException e) {
                parseError = e.getMessage();
            }
        }

        @Override
        public Query parse() throws ParseException {
            if (parseError != null) {
                throw new ParseException ("failed to parse xpath: " + qstr + "; " + parseError);
            }
            Context context = new Context(new ContextSupport());
            XPathQuery query;
            try {
                query = luXPath.getQuery (luXPath.getRootExpr(), context);
            } catch (JaxenException e) {
                throw new ParseException ("failed to parse xpath: " + qstr + "; " + e.getMessage());
            }
            return query;
        }

    }

}
