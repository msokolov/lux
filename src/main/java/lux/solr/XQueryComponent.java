package lux.solr;

import static lux.index.IndexConfiguration.*;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

import javax.xml.transform.TransformerException;

import lux.Compiler;
import lux.DocWriter;
import lux.Evaluator;
import lux.QueryContext;
import lux.TransformErrorListener;
import lux.XdmResultSet;
import lux.exception.LuxException;
import lux.exception.LuxHttpException;
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
    
    public static final String LUX_XQUERY = "lux.xquery";
    public static final String LUX_PATH_INFO = "lux.pathInfo";
    private static final QName LUX_HTTP = new QName (Evaluator.LUX_NAMESPACE, "http");
    protected Set<String> fields = new HashSet<String>();
    protected Compiler compiler;
    private ArrayBlockingQueue<XmlIndexer> indexerPool;
    //protected XmlIndexer indexer;
    protected SolrIndexConfig solrIndexConfig;
    
    public SolrIndexConfig getSolrIndexConfig() {
        return solrIndexConfig;
    }

    private Logger logger;
    
    public XQueryComponent() {
        logger = LoggerFactory.getLogger(XQueryComponent.class);
        indexerPool = new ArrayBlockingQueue<XmlIndexer>(8);
    }
    
    @Override
    public void inform(SolrCore core) {
        // Read the init args from the LuxUpdateProcessorFactory's configuration since we require
        // this plugin to use compatible configuration
        PluginInfo info = core.getSolrConfig().getPluginInfo(UpdateRequestProcessorChain.class.getName());
        solrIndexConfig = SolrIndexConfig.makeIndexConfiguration(INDEX_PATHS|INDEX_FULLTEXT|STORE_DOCUMENT, info.initArgs);
        solrIndexConfig.inform(core);
        compiler = createXCompiler();
    }
    
    public XmlIndexer checkoutXmlIndexer () {
        // In tests it didn't seem to make any appreciable difference whether this
        // pool was present or not, but it salves my conscience
        XmlIndexer indexer = indexerPool.poll();
        if (indexer == null) {
            indexer = new XmlIndexer (solrIndexConfig.getIndexConfig());
        }
        return indexer;
    }
    
    public void returnXmlIndexer (XmlIndexer doneWithIt) {
        indexerPool.offer(doneWithIt);
        // if the pool was full, we just drop the indexer as garbage
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
        DocWriter docWriter = new SolrDocWriter (this, rb.req.getCore());
        Evaluator evaluator = new Evaluator(compiler, new LuxSearcher(searcher), docWriter);
        TransformErrorListener errorListener = evaluator.getErrorListener();
        try {
            String queryPath = rb.req.getParams().get(LUX_XQUERY);
        	expr = compiler.compile(query, errorListener, queryPath == null ? null : java.net.URI.create(queryPath));
        } catch (LuxException ex) {
        	ex.printStackTrace();
        	String err = formatError(query, errorListener);
        	rsp.add("xpath-error", err);
        	evaluator.close();
        	return;
        }
        //SolrIndexSearcher.QueryResult result = new SolrIndexSearcher.QueryResult();
        NamedList<Object> xpathResults = new NamedList<Object>();
        long tstart = System.currentTimeMillis();
        int count = 0;
        QueryContext context = null;
        String xqueryPath = rb.req.getParams().get(LUX_XQUERY);
        if (xqueryPath != null) {
            context = new QueryContext();
            context.bindVariable(LUX_HTTP, buildHttpParams(
                    evaluator,
                    rb.req.getParams(), 
                    xqueryPath
                    ));
        }
        XdmResultSet queryResults = null;
        try {
            queryResults = evaluator.evaluate(expr, context);
        } catch (LuxHttpException e) {
            // TODO: finish this implementation somehow
            // Solr doesn't provide the ability for us to control the HTTP status
            // so we would have to pass back a message to the app server???
            rsp.add ("http-code", e.getCode());
            rsp.add ("http-message", e.getMessage());
            logger.debug ("caught http error: " + e.getCode() + " " + e.getMessage());
        } finally {
            evaluator.close();
        }
        if (queryResults != null) {
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
                String err = formatError(query, queryResults.getErrors());
                rsp.add ("xpath-error", err);
            }
        }
        rsp.add("xpath-results", xpathResults);
        result.setDocList (new DocSlice(0, 0, null, null, evaluator.getQueryStats().docCount, 0));
        rb.setResult (result);
        rsp.add ("response", rb.getResults().docList);
        logger.debug ("retrieved: " + ((Evaluator)evaluator).getDocReader().getCacheMisses() + " docs, " +
                    xpathResults.size() + " results, " + (System.currentTimeMillis() - tstart) + "ms");
    }

    private String formatError(String query, TransformErrorListener errorListener) {
        ArrayList<TransformerException> errors = errorListener.getErrors();
        return formatError(query, errors);
    }

    private String formatError(String query, List<TransformerException> errors) {
        StringBuilder buf = new StringBuilder();
        for (TransformerException te : errors) {
            if (te instanceof XPathException) {
                String additionalLocationText = ((XPathException)te).getAdditionalLocationText();
                if (additionalLocationText != null) {
                    buf.append(additionalLocationText);
                }
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
                if (lineNumber <= lines.length && lineNumber > 0) {
                    String line = lines[lineNumber-1];
                    buf.append (line, Math.min(Math.max(0, column - 100), line.length()), Math.min(line.length(), column + 100));
                }
            }
            logger.error("XQuery exception", te);
        }
        return buf.toString();
    }

    private XdmNode buildHttpParams(Evaluator evaluator, SolrParams params, String path) {
        return (XdmNode) evaluator.build(new StringReader(buildHttpInfo(params)), path);
    }

    private Compiler createXCompiler() {
        return new Compiler(solrIndexConfig.getIndexConfig());
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
    
    // FIXME This may be a bit fragile - I worry we'll have serialization bugs -
    // but the only alternative I can see is to provide a special xquery function
    // and pass the map into the Saxon Evaluator object - but we can't get that
    // from here, and it would be thread-unsafe anyway
    private String buildHttpInfo(SolrParams params) {
        StringBuilder buf = new StringBuilder();
        // TODO: http method
        buf.append (String.format("<http>"));
        buf.append ("<params>");
        Iterator<String> paramNames = params.getParameterNamesIterator();
        while (paramNames.hasNext()) {
            String param = paramNames.next();
            if (param.startsWith("lux.")) {
                continue;
            }
            buf.append(String.format("<param name=\"%s\">", param));
            String[] values = params.getParams(param);
            for (String value : values) {
                buf.append(String.format ("<value>%s</value>", xmlEscape(value)));
            }
            buf.append("</param>");
        }
        buf.append ("</params>");
        String pathInfo = params.get(LUX_PATH_INFO);
        if (pathInfo != null) {
            buf.append("<path-info>").append(xmlEscape(pathInfo)).append("</path-info>");
        }
        // TODO: headers, path, etc?
        buf.append ("</http>");
        return buf.toString();
    }

    private String xmlEscape(String value) {
        return value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll("\"", "&quot;");
    }
    
	public static final String XQUERY_COMPONENT_NAME = "xquery";

    @Override
    public String getDescription() {
        return "XQuery";
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

