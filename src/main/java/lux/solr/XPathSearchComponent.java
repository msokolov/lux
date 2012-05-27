/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.solr;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import lux.api.Evaluator;
import lux.api.Expression;
import lux.api.QueryStats;
import lux.index.XmlIndexer;
import lux.lucene.LuxSearcher;
import lux.xml.XmlBuilder;

import org.apache.solr.common.SolrException;
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

/*
 * dependencies:
 * xml storage - field names
 * xml document model creation from field values - make generic
 * xpath evaluation - use evaluator
 */
public abstract class XPathSearchComponent extends QueryComponent {
    
    protected IndexSchema schema;
    protected Set<String> fields = new HashSet<String>();
    protected Evaluator evaluator;
    
    public XPathSearchComponent() {
        evaluator = createEvaluator();
    }
   
    public abstract Evaluator createEvaluator ();
    
    public abstract Object buildDocument (String xml, XmlBuilder builder);
    
    public abstract void addResult(NamedList<Object> xpathResults, Object result);

    public void prepare(ResponseBuilder rb) throws IOException {        
        SolrParams params = rb.req.getParams();            
        if (rb.getQueryString() == null) {
            rb.setQueryString( params.get( CommonParams.Q ) );
        }
        
        // QParser parser = QParser.getParser(rb.getQueryString(), "xpath", req);
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
        int start = params.getInt( CommonParams.START, -1 );
        if (start < 0) {
          throw new SolrException(SolrException.ErrorCode.BAD_REQUEST, "'start' parameter cannot be negative");
        }
        long timeAllowed = (long)params.getInt( CommonParams.TIME_ALLOWED, -1 );
        int len = params.getInt( CommonParams.ROWS, -1 );
        // multiple shards not implemented
        XmlIndexer indexer = new XmlIndexer(XmlIndexer.INDEX_PATHS);
        evaluator.setIndexer(indexer);
        evaluator.setSearcher(new LuxSearcher(searcher));
        evaluator.setQueryStats(new QueryStats());
        Expression expr = evaluator.compile(rb.getQueryString());
        //SolrIndexSearcher.QueryResult result = new SolrIndexSearcher.QueryResult();
        NamedList<Object> xpathResults = new NamedList<Object>();
        long tstart = System.currentTimeMillis();
        int count = 0;
        for (Object xpathResult : evaluator.iterate(expr, null)) {
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
    

}/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
