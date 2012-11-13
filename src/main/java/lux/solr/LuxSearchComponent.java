package lux.solr;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import lux.api.Evaluator;
import lux.api.Expression;
import lux.api.LuxException;
import lux.api.QueryContext;
import lux.api.QueryStats;
import lux.api.ResultSet;
import lux.index.XmlIndexer;
import lux.saxon.Saxon;
import lux.search.LuxSearcher;
import lux.xpath.QName;

import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This component executes searches expressed as XPath or XQuery.  
 *  Its queries will match documents that have been indexed using XmlIndexer 
 *  with the INDEX_PATHS option.
 */
public abstract class LuxSearchComponent extends QueryComponent {
    
    private static final QName LUX_HTTP_PARAMETERS = new QName ("http://luxproject.net", "http-parameters");
	protected URL baseUri;
    protected IndexSchema schema;
    protected Set<String> fields = new HashSet<String>();
    protected Evaluator evaluator;
    protected XmlIndexer indexer;
    private Logger logger;
    
    public LuxSearchComponent() {
        indexer = new XmlIndexer ();  // FIXME: share with LuxUpdateProcessor?? at least have common config
        evaluator = createEvaluator();
        evaluator.setIndexer(indexer);
        logger = LoggerFactory.getLogger(LuxSearchComponent.class);
    }
    
    public void init(@SuppressWarnings("rawtypes") NamedList args) {
        String base;
        if (args.get("lux.base-uri") != null) {
            base = args.get("lux.content-type").toString();
            try {
                baseUri = new URL (base);
            } catch (MalformedURLException e) {
                logger.error("lux.base-uri " + base + " is malformed: " + e.getMessage());
            }
        } 
        if (baseUri == null) {
            base = System.getProperty("user.dir");
            try {
                baseUri = new File (base).toURI().toURL();
            } catch (MalformedURLException e) { 
                throw new SolrException(ErrorCode.UNKNOWN, base + " is an invalid URL?", e);
            }
        }
    }
    
    public abstract Evaluator createEvaluator ();
        
    public abstract void addResult(NamedList<Object> xpathResults, Object result);

    public void prepare(ResponseBuilder rb) throws IOException {
        SolrQueryRequest req = rb.req;
        SolrParams params = req.getParams();            
        if (rb.getQueryString() == null) {
            rb.setQueryString( params.get( CommonParams.Q ) );
        }
        if (rb.getQueryString() == null) {
            String path = (String) params.get("xquery");
            if (! StringUtils.isBlank(path)) {
                URL absolutePath = new URL (baseUri, path);
                String scheme = absolutePath.getProtocol();
                String contents = null;
                if (scheme.equals("lux")) {
                    // TODO
                } else {
                    contents = IOUtils.toString(absolutePath.openStream());   
                }
                rb.setQueryString(contents);
            }
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
        evaluator.setSearcher(new LuxSearcher(searcher));
        evaluator.setQueryStats(new QueryStats());
        // TODO: catch compilation errors and report using the error reporting
        // used for evaluation errors below
        String query = rb.getQueryString();
        if (StringUtils.isBlank(query)) {
            rsp.add("xpath-error", "query was blank");
        } else {
            evaluateQuery(rb, rsp, result, start, timeAllowed, len, query);
        }
    }

    private void evaluateQuery(ResponseBuilder rb, SolrQueryResponse rsp, SolrIndexSearcher.QueryResult result, int start,
            long timeAllowed, int len, String query) {
        Expression expr;
        try {
        	expr = evaluator.compile(query);
        } catch (LuxException ex) {
        	ex.printStackTrace();
        	rsp.add("xpath-error", ex.getCause().getMessage());
        	return;
        }
        //SolrIndexSearcher.QueryResult result = new SolrIndexSearcher.QueryResult();
        NamedList<Object> xpathResults = new NamedList<Object>();
        long tstart = System.currentTimeMillis();
        int count = 0;
        QueryContext context = new QueryContext();
        context.bindVariable(LUX_HTTP_PARAMETERS, buildHttpParams());
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
        logger.debug ("retrieved: " + ((Saxon)evaluator).getDocReader().getCacheMisses() + " docs, " +
                    xpathResults.size() + " results, " + (System.currentTimeMillis() - tstart) + "ms");
    }

    private XdmNode buildHttpParams() {
	    // TODO Auto-generated method stub
	    return null;
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
        return "0.4";
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
