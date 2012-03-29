package lux.solr;

import lux.api.LuxException;
import lux.saxon.Saxon;

import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.Query;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.search.QParser;

public class SaxonParser extends QParser {

    private Saxon saxon;
    
    public SaxonParser(XPathQueryPlugin xPathQueryParserFactory, String qstr, SolrParams localParams, SolrParams params, SolrParams config, SolrQueryRequest req) {
        super(qstr, localParams, params, req);
        // TODO: retrieve the Saxon from a pool in case this is expensive!
        saxon = new Saxon();
    }

    @Override
    public Query parse() throws ParseException {
        try {
            return saxon.compile(qstr).getXPathQuery();
        } catch (LuxException e) {
            throw new ParseException ("failed to parse xpath: " + qstr + "; " + e.getMessage());
        }
    }

}