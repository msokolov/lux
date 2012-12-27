package lux.solr;

import static lux.index.IndexConfiguration.INDEX_FULLTEXT;
import static lux.index.IndexConfiguration.INDEX_PATHS;
import static lux.index.IndexConfiguration.STORE_XML;

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

import lux.Compiler;
import lux.DocWriter;
import lux.Evaluator;
import lux.QueryContext;
import lux.XdmResultSet;
import lux.exception.LuxException;
import lux.index.XmlIndexer;
import lux.search.LuxSearcher;
import lux.xml.QName;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.trans.XPathException;
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
import org.apache.solr.core.PluginInfo;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.QueryComponent;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocSlice;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.update.processor.UpdateRequestProcessorChain;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This component executes searches expressed as XPath or XQuery.
 *  Its queries will match documents that have been indexed using XmlIndexer
 *  with the INDEX_PATHS option.
 */
public class XQueryComponent extends QueryComponent implements SolrCoreAware {

    private static final QName LUX_HTTP = new QName ("http://luxproject.net", "http");
    protected URL baseUri;
    protected Set<String> fields = new HashSet<String>();
    protected Compiler compiler;
    protected XmlIndexer indexer;
    protected SolrIndexConfig solrIndexConfig;
    
    private Logger logger;
    
    public XQueryComponent() {
        logger = LoggerFactory.getLogger(XQueryComponent.class);
    }
    
    @Override
    public void init(@SuppressWarnings("rawtypes") NamedList args) {
        String base;
        if (args.get("lux.base-uri") != null) {
            base = args.get("lux.base-uri").toString();
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
    
    @Override
    public void inform(SolrCore core) {
        // Read the init args from the LuxUpdateProcessorFactory's configuration since we require
        // this plugin to use compatible configuration
        PluginInfo info = core.getSolrConfig().getPluginInfo(UpdateRequestProcessorChain.class.getName());
        solrIndexConfig = SolrIndexConfig.makeIndexConfiguration(INDEX_PATHS|INDEX_FULLTEXT|STORE_XML, info.initArgs);
        solrIndexConfig.inform(core);
        indexer = new XmlIndexer (solrIndexConfig.getIndexConfig());
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
        SolrParams params = req.getParams();
        if (!params.getBool(XQUERY_COMPONENT_NAME, true)) {
          return;
        }
        int start = params.getInt( CommonParams.START, 1 );
        int len = params.getInt( CommonParams.ROWS, -1 );
        // multiple shards not implemented
        evaluateQuery(rb, start, len);
    }

    protected void evaluateQuery(ResponseBuilder rb, int start, int len) {
        String query = rb.getQueryString();
        SolrQueryRequest req = rb.req;
        SolrQueryResponse rsp = rb.rsp;
        if (StringUtils.isBlank(query)) {
            rsp.add("xpath-error", "query was blank");
            return;
        }
        SolrParams params = req.getParams();
        long timeAllowed = (long)params.getInt( CommonParams.TIME_ALLOWED, -1 );
        if (!params.getBool(XQUERY_COMPONENT_NAME, true)) {
            return;
        }
        XQueryExecutable expr;
        SolrIndexSearcher.QueryResult result = new SolrIndexSearcher.QueryResult();
        SolrIndexSearcher searcher = rb.req.getSearcher();
        DocWriter docWriter = new SolrDocWriter (indexer, rb.req.getCore().getUpdateHandler());
        Evaluator evaluator = new Evaluator(compiler, new LuxSearcher(searcher), docWriter);
        try {
            // TODO: pass in (String) params.get(LuxServlet.LUX_XQUERY) as the systemId
            // of the query so error reporting will be able to report it
            // and so it can include modules with relative paths
            // String queryPath = rb.req.getParams().get(LuxServlet.LUX_XQUERY);
        	expr = compiler.compile(query);
        } catch (LuxException ex) {
        	ex.printStackTrace();
        	StringBuilder buf = new StringBuilder ();
        	for (TransformerException te : compiler.getErrorListener().getErrors()) {
        	    if (te instanceof XPathException) {
        	        buf.append(((XPathException)te).getAdditionalLocationText());
        	    }
        	    buf.append (te.getMessageAndLocation());
        	    buf.append ("\n");
        	    if (te.getLocator() != null) {
        	        int lineNumber = te.getLocator().getLineNumber();
        	        int column = te.getLocator().getColumnNumber();
        	        if (compiler.getLastOptimized() != null) {
        	            query = compiler.getLastOptimized().toString();
        	        }
        	        String[] lines = query.split("\r?\n");
        	        if (lineNumber <= lines.length) {
        	            String line = lines[lineNumber-1];
        	            buf.append (line, Math.max(0, column - 60), Math.min(line.length(), column + 60));
        	        }
        	    }
        	}
        	rsp.add("xpath-error", buf.toString());
        	evaluator.close();
        	return;
        }
        //SolrIndexSearcher.QueryResult result = new SolrIndexSearcher.QueryResult();
        NamedList<Object> xpathResults = new NamedList<Object>();
        long tstart = System.currentTimeMillis();
        int count = 0;
        QueryContext context = null;
        String xqueryPath = rb.req.getParams().get(AppServerRequestFilter.LUX_XQUERY);
        if (xqueryPath != null) {
            context = new QueryContext();
            context.bindVariable(LUX_HTTP, buildHttpParams(
                    evaluator,
                    rb.req.getParams().get(AppServerRequestFilter.LUX_HTTPINFO), 
                    xqueryPath
                    ));
        }
        XdmResultSet queryResults = evaluator.evaluate(expr, context);
        evaluator.close();
        if (queryResults.getErrors().isEmpty()) {
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
        }
        else {
            for (TransformerException te : queryResults.getErrors()) {
                if (te instanceof XPathException) {
                    XPathException xpe = (XPathException) te;
                    if (((XPathException) te).getAdditionalLocationText() != null) {
                        rsp.add ("xpath-error", xpe.getAdditionalLocationText());
                    }
                }
                rsp.add("xpath-error", te.getMessageAndLocation());
            }
        }
        rsp.add("xpath-results", xpathResults);
        result.setDocList (new DocSlice(0, 0, null, null, evaluator.getQueryStats().docCount, 0));
        rb.setResult (result);
        rsp.add ("response", rb.getResults().docList);
        logger.debug ("retrieved: " + ((Evaluator)evaluator).getDocReader().getCacheMisses() + " docs, " +
                    xpathResults.size() + " results, " + (System.currentTimeMillis() - tstart) + "ms");
    }

    private XdmNode buildHttpParams(Evaluator evaluator, String http, String path) {
        return (XdmNode) evaluator.build(new StringReader(http), path);
    }

    private Compiler createXCompiler() {
        return new Compiler(indexer.getConfiguration());
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
	public static final String XQUERY_COMPONENT_NAME = "xquery";

    @Override
    public String getDescription() {
        return "XQuery";
    }

    @Override
    public String getSourceId() {
        return "lux.XQueryComponent";
    }

    @Override
    public String getSource() {
        return "http://falutin.net/svn";
    }

    @Override
    public String getVersion() {
        return "0.5";
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

