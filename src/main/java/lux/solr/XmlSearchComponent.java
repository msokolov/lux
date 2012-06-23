package lux.solr;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import lux.api.Evaluator;
import lux.api.Expression;
import lux.api.QueryStats;
import lux.api.ResultSet;
import lux.index.XmlIndexer;
import lux.search.LuxSearcher;

import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.QueryComponent;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.search.DocSlice;
import org.apache.solr.search.SolrIndexSearcher;

/** This component executes searches expressed as XPath or XQuery.  
 *  Its queries will match documents that have been indexed using XmlIndexer 
 *  with the INDEX_PATHS option.
 */
public abstract class XmlSearchComponent extends QueryComponent {
    
    protected IndexSchema schema;
    protected Set<String> fields = new HashSet<String>();
    protected Evaluator evaluator;
    
    public XmlSearchComponent() {
        evaluator = createEvaluator();
    }
   
    public abstract Evaluator createEvaluator ();
        
    public abstract void addResult(NamedList<Object> xpathResults, Object result);

    public void prepare(ResponseBuilder rb) throws IOException {        
        SolrParams params = rb.req.getParams();            
        if (rb.getQueryString() == null) {
            rb.setQueryString( params.get( CommonParams.Q ) );
        }
    }
    
    @Override
    public void process(ResponseBuilder rb) throws IOException {

        SolrQueryRequest req = rb.req;
        SolrQueryResponse rsp = rb.rsp;
        SolrParams params = req.getParams();
        if (!params.getBool(COMPONENT_NAME, true)) {
          return;
        }
        SolrIndexSearcher searcher = req.getSearcher();
        SolrIndexSearcher.QueryResult result = new SolrIndexSearcher.QueryResult();
        // ignored for now
        int start = params.getInt( CommonParams.START, 1 );
        long timeAllowed = (long)params.getInt( CommonParams.TIME_ALLOWED, -1 );
        int len = params.getInt( CommonParams.ROWS, -1 );
        // multiple shards not implemented
        XmlIndexer indexer = new XmlIndexer(XmlIndexer.INDEX_PATHS);
        evaluator.setIndexer(indexer);
        evaluator.setSearcher(new LuxSearcher(searcher));
        evaluator.setQueryStats(new QueryStats());
        // TODO: catch compilation errors and report using the error reporting
        // used for evaluation errors below
        Expression expr = evaluator.compile(rb.getQueryString());
        //SolrIndexSearcher.QueryResult result = new SolrIndexSearcher.QueryResult();
        NamedList<Object> xpathResults = new NamedList<Object>();
        long tstart = System.currentTimeMillis();
        int count = 0;
        ResultSet<?> queryResults = evaluator.iterate(expr, null);
        for (Object xpathResult : queryResults) {
            if (++ count < start) {
                continue;
            }
            addResult (xpathResults, xpathResult);
            if ((len > 0 && xpathResults.size() >= len) || 
                    (timeAllowed > 0 && (System.currentTimeMillis() - tstart) > timeAllowed)) {
                break;
            }
        }        
        rsp.add("xpath-results", xpathResults);
        result.setDocList (new DocSlice(0, 0, null, null, evaluator.getQueryStats().docCount, 0));
        Exception ex = queryResults.getException();
        if (ex != null) {
            rsp.add("xpath-error", ex.getMessage());
        }
        rb.setResult (result);
        rsp.add ("response", rb.getResults().docList);
    }

    public static final String COMPONENT_NAME = "xpath";

    @Override
    public String getDescription() {
        return "XPath";
    }

    @Override
    public String getSourceId() {
        return "lux.XPathSearchComponent";
    }

    @Override
    public String getSource() {
        return "http://falutin.net/svn";
    }

    @Override
    public String getVersion() {
        return "0.1";
    }
    

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
