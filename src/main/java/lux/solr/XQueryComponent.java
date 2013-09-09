package lux.solr;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.transform.TransformerException;

import lux.DocWriter;
import lux.Evaluator;
import lux.QueryStats;
import lux.TransformErrorListener;
import lux.exception.LuxException;
import lux.exception.ResourceExhaustedException;
import lux.search.LuxSearcher;
import lux.xml.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.TypeHierarchy;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.DecimalValue;
import net.sf.saxon.value.GDateValue;
import net.sf.saxon.value.GDayValue;
import net.sf.saxon.value.GMonthDayValue;
import net.sf.saxon.value.GMonthValue;
import net.sf.saxon.value.GYearMonthValue;
import net.sf.saxon.value.GYearValue;
import net.sf.saxon.value.QNameValue;
import net.sf.saxon.value.Value;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.QueryComponent;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocSlice;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This component executes searches expressed as XPath or XQuery. Its queries
 * will match documents that have been indexed using XmlIndexer.
 */
public class XQueryComponent extends QueryComponent implements SolrCoreAware {

    public static final String LUX_XQUERY = "lux.xquery";
    public static final String LUX_PATH_INFO = "lux.pathInfo";
    private static final QName LUX_HTTP = new QName(Evaluator.LUX_NAMESPACE, "http");
    // TODO: expose via configuration
    private static final int MAX_RESULT_SIZE = (int) (Runtime.getRuntime().maxMemory() / 32);

    protected Set<String> fields = new HashSet<String>();

    protected SolrIndexConfig solrIndexConfig;
    protected SearchHandler searchHandler;

    protected String queryPath;

    private Serializer serializer;

    private Logger logger;
    
    private SolrCore core;

    private int resultByteSize;

    public XQueryComponent() {
        logger = LoggerFactory.getLogger(XQueryComponent.class);
    }

    @Override
    public void inform(SolrCore core) {
        solrIndexConfig = SolrIndexConfig.registerIndexConfiguration(core);
        this.core = core;
    }
    
    private void findSearchHandler () {
        for (SolrRequestHandler handler : core.getRequestHandlers().values()) {
            if (handler instanceof SearchHandler) {
                List<SearchComponent> components = ((SearchHandler) handler).getComponents();
                if (components != null) {
                    for (SearchComponent component : components) {
                        if (component == this) {
                            searchHandler = (SearchHandler) handler;
                            break;
                        }
                    }
                }
            }
        }
    }
    
    @Override
    public void prepare(ResponseBuilder rb) throws IOException {
        if (searchHandler == null) {
            // bleah -- we need a link to the search handler to pass down in to the bowels of
            // XQuery evaluation so we can recurse when we come to a search call.  To get that,
            // we can only traverse the core registry, but due to order of initialization, the
            // handler won't have been linked to this component until after all the inform() calls 
            // are done.
            //  A possible alternative here would be to write our own search handler that extends
            // the Solr one and adds itself to the ResponseBuilder...
            findSearchHandler ();
        }
        SolrQueryRequest req = rb.req;
        SolrParams params = req.getParams();
        if (rb.getQueryString() == null) {
            rb.setQueryString(params.get(CommonParams.Q));
        }
        String contentType = params.get("lux.contentType");
        serializer = solrIndexConfig.checkoutSerializer();
        if (contentType != null) {
            if (contentType.equals("text/html")) {
                serializer.setOutputProperty(Serializer.Property.METHOD, "html");
            } else if (contentType.equals("text/xml")) {
                serializer.setOutputProperty(Serializer.Property.METHOD, "xml");
            }
        } else {
            serializer.setOutputProperty(Serializer.Property.METHOD, getDefaultSerialization());
        }
        if (queryPath == null) {
            // allow subclasses to override...
            queryPath = rb.req.getParams().get(LUX_XQUERY);
        }
        resultByteSize = 0;
    }

    public String getDefaultSerialization() {
        return "xml";
    }

    @Override
    public void process(ResponseBuilder rb) throws IOException {
        if (rb.grouping()) {
            throw new SolrException(ErrorCode.BAD_REQUEST, "grouping not supported for XQuery");
        }
        SolrQueryRequest req = rb.req;
        SolrParams params = req.getParams();
        if (!params.getBool(XQUERY_COMPONENT_NAME, true)) {
            // TODO -- what is this for? who would pass xquery=false??
            return;
        }
        int start = params.getInt(CommonParams.START, 1);
        int len = params.getInt(CommonParams.ROWS, -1);
        try {
            evaluateQuery(rb, start, len);
        } finally {
            solrIndexConfig.returnSerializer(serializer);
        }
    }
    
    /**
     * Process for a distributed search. This method is called at various stages
     * during the processing of a request:
     * 
     * During ResponseBuilder.STAGE_PARSE_QUERY we parse, optimize, compile and
     * execute the XQuery query. When a lux:search call is encountered, it will
     * create a SearchResultIterator, which creates a Lucene Query and calls
     * back into the SearchHandler; then subsequent phases are handled by the
     * normal QueryComponent.
     * 
     * @return the next stage for this component
     */
    @Override
    public int distributedProcess(ResponseBuilder rb) throws IOException {
        if (rb.grouping()) {
            throw new SolrException(ErrorCode.BAD_REQUEST, "Solr grouping not supported for XQuery");
        }
        if (rb.stage == ResponseBuilder.STAGE_PARSE_QUERY) {
            if (rb.req instanceof CloudQueryRequest) {
                CloudQueryRequest cloudReq = (CloudQueryRequest) rb.req;
                // the sort spec has already been generated
                rb.setSortSpec(cloudReq.getSortSpec());
                return ResponseBuilder.STAGE_EXECUTE_QUERY;
            } else {
                process(rb);
                return ResponseBuilder.STAGE_DONE;
            }
        } else {
            return super.distributedProcess(rb);
        }
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
        long timeAllowed = (long) params.getInt(CommonParams.TIME_ALLOWED, -1);
        if (!params.getBool(XQUERY_COMPONENT_NAME, true)) {
            return;
        }
        XQueryExecutable expr;
        SolrIndexSearcher.QueryResult result = new SolrIndexSearcher.QueryResult();
        SolrIndexSearcher searcher = rb.req.getSearcher();
        DocWriter docWriter = new SolrDocWriter(this, rb.req.getCore());
        lux.Compiler compiler = solrIndexConfig.getCompiler();

        Evaluator evaluator = new Evaluator(compiler, new LuxSearcher(searcher), docWriter);
        TransformErrorListener errorListener = evaluator.getErrorListener();
        try {
            URI baseURI = queryPath == null ? null : java.net.URI.create(queryPath);
            expr = compiler.compile(query, errorListener, baseURI, null);
        } catch (LuxException ex) {
            // ex.printStackTrace();
            String err = formatError(query, errorListener);
            if (StringUtils.isEmpty(err)) {
                err = ex.getMessage();
            }
            rsp.add("xpath-error", err);
            // don't close: this forces a commit()
            // evaluator.close();
            return;
        }
        // SolrIndexSearcher.QueryResult result = new
        // SolrIndexSearcher.QueryResult();
        NamedList<Object> xpathResults = new NamedList<Object>();
        long tstart = System.currentTimeMillis();
        int count = 0;
        SolrQueryContext context = new SolrQueryContext(this);
        if (rb.shards != null) {
            // This is a distributed request; pass in the ResponseBuilder so it will be
            // available to a subquery.
            context.setResponseBuilder(rb);
        }
        String xqueryPath = rb.req.getParams().get(LUX_XQUERY);
        if (xqueryPath != null) {
            context.bindVariable(LUX_HTTP,
                    buildHttpParams(evaluator, rb.req.getParams(), rb.req.getContext(), xqueryPath));
        }
        Iterator<XdmItem> queryResults = evaluator.iterator(expr, context);
        String err = null;
        while (queryResults.hasNext()) {
            XdmItem xpathResult = queryResults.next();
            if (++count < start) {
                continue;
            }
            try {
                addResult(xpathResults, xpathResult);
            } catch (SaxonApiException e) {
                err = e.getMessage();
                xpathResults = null;
                break;
            } catch (ResourceExhaustedException e) {
                err = e.getMessage();
                break;
            } catch (OutOfMemoryError e) {
                xpathResults = null;
                err = e.getMessage();
                break;
            }
            if ((len > 0 && xpathResults.size() >= len)
                    || (timeAllowed > 0 && (System.currentTimeMillis() - tstart) > timeAllowed)) {
                break;
            }
        }
        ArrayList<TransformerException> errors = evaluator.getErrorListener().getErrors();
        if (!errors.isEmpty()) {
            err = formatError(query, errors, evaluator.getQueryStats());
            if (xpathResults.size() == 0) {
                xpathResults = null; // throw a 400 error; don't return partial
                                     // results
            }
        }
        if (err != null) {
            rsp.add("xpath-error", err);
        }
        rsp.add("xpath-results", xpathResults);
        result.setDocList(new DocSlice(0, 0, null, null, evaluator.getQueryStats().docCount, 0));
        rb.setResult(result);
        rsp.add("response", rb.getResults().docList);
        if (xpathResults != null) {
            if (logger.isDebugEnabled()) {
                logger.debug("retrieved: " + ((Evaluator) evaluator).getDocReader().getCacheMisses() + " docs, "
                        + xpathResults.size() + " results, " + (System.currentTimeMillis() - tstart) + "ms");
            }
        } else {
            logger.warn("xquery evaluation error: " + ((Evaluator) evaluator).getDocReader().getCacheMisses()
                    + " docs, " + "0 results, " + (System.currentTimeMillis() - tstart) + "ms");
        }
    }

    private String formatError(String query, TransformErrorListener errorListener) {
        ArrayList<TransformerException> errors = errorListener.getErrors();
        return formatError(query, errors, null);
    }

    private String formatError(String query, List<TransformerException> errors, QueryStats queryStats) {
        StringBuilder buf = new StringBuilder();
        if (queryStats != null && queryStats.optimizedQuery != null) {
            query = queryStats.optimizedQuery;
        }
        for (TransformerException te : errors) {
            if (te instanceof XPathException) {
                String additionalLocationText = ((XPathException) te).getAdditionalLocationText();
                if (additionalLocationText != null) {
                    buf.append(additionalLocationText);
                }
            }
            buf.append(te.getMessageAndLocation());
            buf.append("\n");
            if (te.getLocator() != null) {
                int lineNumber = te.getLocator().getLineNumber();
                int column = te.getLocator().getColumnNumber();
                String[] lines = query.split("\r?\n");
                if (lineNumber <= lines.length && lineNumber > 0) {
                    String line = lines[lineNumber - 1];
                    buf.append(line, Math.min(Math.max(0, column - 100), line.length()),
                            Math.min(line.length(), column + 100));
                }
            }
            logger.error("XQuery exception", te);
        }
        return buf.toString();
    }

    private XdmNode buildHttpParams(Evaluator evaluator, SolrParams params, Map<Object, Object> context, String path) {
        return (XdmNode) evaluator.build(new StringReader(buildHttpInfo(params, context)), path);
    }

    protected void addResult(NamedList<Object> xpathResults, XdmItem item) throws SaxonApiException {
        if (item.isAtomicValue()) {
            // We need to get Java primitive values that Solr knows how to
            // marshal
            XdmAtomicValue xdmValue = (XdmAtomicValue) item;
            AtomicValue value = (AtomicValue) xdmValue.getUnderlyingValue();
            TypeHierarchy typeHierarchy = solrIndexConfig.getCompiler().getProcessor().getUnderlyingConfiguration()
                    .getTypeHierarchy();
            try {
                String typeName = value.getItemType(typeHierarchy).toString();
                Object javaValue;
                if (value instanceof DecimalValue) {
                    javaValue = ((DecimalValue) value).getDoubleValue();
                    addResultBytes(8);
                } else if (value instanceof QNameValue) {
                    javaValue = ((QNameValue) value).getClarkName();
                    addResultBytes(((String) javaValue).length() * 2); // close
                                                                       // enough,
                                                                       // modulo
                                                                       // surrogates
                } else if (value instanceof GDateValue) {
                    if (value instanceof GMonthValue) {
                        javaValue = ((GMonthValue) value).getPrimitiveStringValue().toString();
                    } else if (value instanceof GYearValue) {
                        javaValue = ((GYearValue) value).getPrimitiveStringValue().toString();
                    } else if (value instanceof GDayValue) {
                        javaValue = ((GDayValue) value).getPrimitiveStringValue().toString();
                    } else if (value instanceof GMonthDayValue) {
                        javaValue = ((GMonthDayValue) value).getPrimitiveStringValue().toString();
                    } else if (value instanceof GYearMonthValue) {
                        javaValue = ((GYearMonthValue) value).getPrimitiveStringValue().toString();
                    } else {
                        javaValue = Value.convertToJava(value);
                    }
                    addResultBytes(javaValue.toString().length() * 2);
                } else {
                    javaValue = Value.convertToJava(value);
                    addResultBytes(javaValue.toString().length() * 2);
                }
                // TODO hexBinary and base64Binary
                xpathResults.add(typeName, javaValue);
            } catch (XPathException e) {
                xpathResults.add(value.getPrimitiveType().getDisplayName(), value.toString());
            }
        } else {
            XdmNode node = (XdmNode) item;
            XdmNodeKind nodeKind = node.getNodeKind();
            StringWriter buf = new StringWriter();
            // TODO: xml serialization, indentation control; for now assume
            // text/html
            // TODO: tinybin serialization!
            serializer.setOutputWriter(buf);
            serializer.serializeNode(node);
            String xml = buf.toString();
            addResultBytes(xml.length() * 2);
            xpathResults.add(nodeKind.toString().toLowerCase(), xml);
        }
    }

    private void addResultBytes(int count) {
        if (resultByteSize + count > MAX_RESULT_SIZE) {
            throw new ResourceExhaustedException("Maximum result size exceeded, returned result has been truncated");
        }
        resultByteSize += count;
    }

    // Hand-coded serialization may be a bit fragile, but the only alternative
    // using Saxon is too inconvenient
    private String buildHttpInfo(SolrParams params, Map<Object, Object> context) {
        StringBuilder buf = new StringBuilder();
        // TODO: http method
        buf.append(String.format("<http>"));
        buf.append("<params>");
        Iterator<String> paramNames = params.getParameterNamesIterator();
        while (paramNames.hasNext()) {
            String param = paramNames.next();
            if (param.startsWith("lux.")) {
                continue;
            }
            buf.append(String.format("<param name=\"%s\">", param));
            String[] values = params.getParams(param);
            for (String value : values) {
                buf.append(String.format("<value>%s</value>", xmlEscape(value)));
            }
            buf.append("</param>");
        }
        buf.append("</params>");
        String pathInfo = params.get(LUX_PATH_INFO);
        if (pathInfo != null) {
            buf.append("<path-info>").append(xmlEscape(pathInfo)).append("</path-info>");
        }
        String webapp = (String) context.get("webapp");
        if (webapp == null) {
            webapp = "";
        }
        buf.append("<context-path>").append(webapp).append("</context-path>");
        // TODO: headers, path, etc?
        buf.append("</http>");
        return buf.toString();
    }

    public SolrIndexConfig getSolrIndexConfig() {
        return solrIndexConfig;
    }

    public SearchHandler getSearchHandler() {
        return searchHandler;
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
        return "http://github.com/msokolov/lux";
    }

    @Override
    public String getVersion() {
        return "";
    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */

