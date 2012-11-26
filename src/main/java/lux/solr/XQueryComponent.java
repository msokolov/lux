package lux.solr;

import static lux.index.IndexConfiguration.INDEX_PATHS;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import javax.xml.transform.TransformerException;

import lux.DocWriter;
import lux.Evaluator;
import lux.QueryContext;
import lux.XCompiler;
import lux.XdmResultSet;
import lux.exception.LuxException;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.search.LuxSearcher;
import lux.xml.QName;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.FloatValue;
import net.sf.saxon.value.QNameValue;

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
public class XQueryComponent extends QueryComponent {
    
    private static final QName LUX_HTTP = new QName ("http://luxproject.net", "http");
    protected URL baseUri;
    protected IndexSchema schema;
    protected Set<String> fields = new HashSet<String>();
    protected XCompiler compiler;
    protected XmlIndexer indexer;
    private Logger logger;
    
    public XQueryComponent() {
        logger = LoggerFactory.getLogger(XQueryComponent.class);
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
        IndexConfiguration config = LuxUpdateProcessorFactory.makeIndexConfiguration(INDEX_PATHS, args);
        // FIXME: this requires duplicated config for field aliases - one for the update processor and one for this
        indexer = new XmlIndexer (config);
        compiler = createXCompiler();
    }

    @Override
    public void prepare(ResponseBuilder rb) throws IOException {
        SolrQueryRequest req = rb.req;
        SolrParams params = req.getParams();            
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
        //req.getCore().getUpdateHandler().
        SolrIndexSearcher searcher = req.getSearcher();
        SolrIndexSearcher.QueryResult result = new SolrIndexSearcher.QueryResult();
        // ignored for now
        int start = params.getInt( CommonParams.START, 1 );
        long timeAllowed = (long)params.getInt( CommonParams.TIME_ALLOWED, -1 );
        int len = params.getInt( CommonParams.ROWS, -1 );
        // multiple shards not implemented
        DocWriter docWriter = new SolrDocWriter (indexer, req.getCore().getUpdateHandler());
        Evaluator evaluator = new Evaluator(compiler, new LuxSearcher(searcher), docWriter);
        String query = rb.getQueryString();
        if (StringUtils.isBlank(query)) {
            rsp.add("xpath-error", "query was blank");
        } else {
            evaluateQuery(rb, rsp, result, evaluator, start, timeAllowed, len, query);
        }
    }

    protected void evaluateQuery(ResponseBuilder rb, SolrQueryResponse rsp, SolrIndexSearcher.QueryResult result, 
            Evaluator evaluator,
            int start, long timeAllowed, int len, String query) {
        XQueryExecutable expr;
        XCompiler compiler = evaluator.getCompiler();
        try {
            // TODO: pass in (String) params.get(LuxServlet.LUX_XQUERY) as the systemId
            // of the query so error reporting will be able to report it
            // and so it can include modules with relative paths
        	expr = compiler.compile(query);
        } catch (LuxException ex) {
        	ex.printStackTrace();
        	StringBuilder buf = new StringBuilder ();
        	for (TransformerException te : compiler.getErrorListener().getErrors()) {
        	    buf.append (te.getMessageAndLocation());
        	    buf.append ("\n");
        	}
        	rsp.add("xpath-error", buf.toString());
        	return;
        }
        //SolrIndexSearcher.QueryResult result = new SolrIndexSearcher.QueryResult();
        NamedList<Object> xpathResults = new NamedList<Object>();
        long tstart = System.currentTimeMillis();
        int count = 0;
        QueryContext context = null;
        String xqueryPath = rb.req.getParams().get(LuxServlet.LUX_XQUERY);
        if (xqueryPath != null) {
            context = new QueryContext();
            context.bindVariable(LUX_HTTP, buildHttpParams(
                    evaluator,
                    rb.req.getParams().get(LuxServlet.LUX_HTTPINFO), 
                    xqueryPath
                    ));
        }
        XdmResultSet queryResults = evaluator.iterate(expr, context);
        for (Object xpathResult : queryResults) {
            if (++ count < start) {
                continue;
            }
            addResult (xpathResults, (XdmItem) xpathResult);
            if ((len > 0 && xpathResults.size() >= len) || 
                    (timeAllowed > 0 && (System.currentTimeMillis() - tstart) > timeAllowed)) {
                break;
            }
        }
        rsp.add("xpath-results", xpathResults);
        result.setDocList (new DocSlice(0, 0, null, null, evaluator.getQueryStats().docCount, 0));
        if (queryResults.getErrors() != null) {
            for (TransformerException te : queryResults.getErrors()) {
                if (te.getLocator() != null) {
                    rsp.add("xpath-error", te.getMessage() + " on line " + te.getLocator().getLineNumber() + " at column " + te.getLocator().getColumnNumber());
                } else {
                    rsp.add("xpath-error", te.getMessage());
                }
            }
        }
        rb.setResult (result);
        rsp.add ("response", rb.getResults().docList);
        logger.debug ("retrieved: " + ((Evaluator)evaluator).getDocReader().getCacheMisses() + " docs, " +
                    xpathResults.size() + " results, " + (System.currentTimeMillis() - tstart) + "ms");
    }

    private XdmNode buildHttpParams(Evaluator evaluator, String http, String path) {
        return (XdmNode) evaluator.getBuilder().build(new StringReader(http), path);
    }

    private XCompiler createXCompiler() {
        return new XCompiler(indexer.getConfiguration());
    }
    
    protected void addResult(NamedList<Object> xpathResults, XdmItem item) {
        if (item.isAtomicValue()) {
            XdmAtomicValue xdmValue = (XdmAtomicValue) item;
            Object value = xdmValue.getUnderlyingValue();
            if (value instanceof String) {
                xpathResults.add ("xs:string", xdmValue.toString());
            } else if (value instanceof BigInteger) {
                xpathResults.add ("xs:int", xdmValue.getValue());
            } else if (value instanceof DoubleValue) {
                xpathResults.add ("xs:double", xdmValue.toString());
            } else if (value instanceof FloatValue) {
                xpathResults.add ("xs:float", xdmValue.toString());
            } else if (value instanceof BooleanValue) {
                xpathResults.add ("xs:boolean", xdmValue.toString());
            } else if (value instanceof BigDecimal) {
                xpathResults.add ("xs:decimal", xdmValue.toString());
            } else if (value instanceof QNameValue) {
                xpathResults.add ("xs:QName", xdmValue.toString());
            } else  {
                // no way to distinguish xs:anyURI, xs:untypedAtomic or um something else
                xpathResults.add ("xs:string", xdmValue.toString());
            } 
        } else {
            XdmNode node = (XdmNode) item;
            XdmNodeKind nodeKind = node.getNodeKind();
            xpathResults.add(nodeKind.toString().toLowerCase(), node.toString());
        }
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
