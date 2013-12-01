package lux.solr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;

import lux.Compiler;
import lux.DocWriter;
import lux.Evaluator;
import lux.QueryContext;
import lux.QueryStats;
import lux.TransformErrorListener;
import lux.exception.LuxException;
import lux.exception.ResourceExhaustedException;
import lux.search.LuxSearcher;
import lux.solr.LuxDispatchFilter.Request;
import lux.xml.QName;
import net.sf.saxon.om.FingerprintedQName;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.query.GlobalVariableDefinition;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.linked.LinkedTreeBuilder;
import net.sf.saxon.tree.tiny.TinyElementImpl;
import net.sf.saxon.type.AnyType;
import net.sf.saxon.type.BuiltInAtomicType;
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
import net.sf.saxon.value.TextFragmentValue;
import net.sf.saxon.value.Value;
import nu.validator.htmlparser.sax.HtmlParser;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.ContentStream;
import org.apache.solr.common.util.ContentStreamBase;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.QueryComponent;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocSlice;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

/** This component executes searches expressed as XPath or XQuery.
 *  Its queries will match documents that have been indexed using XmlIndexer
 *  with the INDEX_PATHS option.
 */
public class XQueryComponent extends QueryComponent implements SolrCoreAware {
    
    public static final String LUX_XQUERY = "lux.xquery";
    public static final String LUX_PATH_INFO = "lux.pathInfo";
    private static final QName LUX_HTTP = new QName (Evaluator.LUX_NAMESPACE, "http");
    // TODO: expose via configuration
    private static final int MAX_RESULT_SIZE = (int) (Runtime.getRuntime().maxMemory() / 32);

    protected Set<String> fields = new HashSet<String>();

    protected SolrIndexConfig solrIndexConfig;
    
    protected String queryPath;
    
    private Serializer serializer;
    
    private Logger logger;
    
    private int resultByteSize;
    
    public XQueryComponent() {
        logger = LoggerFactory.getLogger(XQueryComponent.class);
    }
    
    @Override
    public void inform(SolrCore core) {
        // FIXME: this should be a per-core resource, not a singleton, or it should store info per-core internally
        solrIndexConfig = SolrIndexConfig.registerIndexConfiguration(core);
    }

    @Override
    public void prepare(ResponseBuilder rb) throws IOException {
        SolrQueryRequest req = rb.req;
        SolrParams params = req.getParams();            
        if (rb.getQueryString() == null) {
            rb.setQueryString( params.get( CommonParams.Q ) );
        }
        String contentType= params.get("lux.contentType");
        serializer = solrIndexConfig.checkoutSerializer();
        if (contentType != null) {
            if (contentType.equals ("text/html")) {
                serializer.setOutputProperty(Serializer.Property.METHOD, "html");
            } else if (contentType.equals ("text/xml")) {
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
    
    public String getDefaultSerialization () {
        return "xml";
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
        try {
            evaluateQuery(rb, start, len);
        } finally {
            solrIndexConfig.returnSerializer(serializer);
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
        long timeAllowed = (long)params.getInt( CommonParams.TIME_ALLOWED, -1 );
        if (!params.getBool(XQUERY_COMPONENT_NAME, true)) {
            return;
        }
        XQueryExecutable expr;
        SolrIndexSearcher.QueryResult result = new SolrIndexSearcher.QueryResult();
        SolrIndexSearcher searcher = rb.req.getSearcher();
        DocWriter docWriter = new SolrDocWriter (this, rb.req.getCore());
        Compiler compiler = solrIndexConfig.getCompiler();

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
        //SolrIndexSearcher.QueryResult result = new SolrIndexSearcher.QueryResult();
        NamedList<Object> xpathResults = new NamedList<Object>();
        long tstart = System.currentTimeMillis();
        int count = 0;
        QueryContext context = null;
        context = new QueryContext();
        bindRequestVariables(rb, req, expr, compiler, evaluator, context);
        Iterator<XdmItem> queryResults = evaluator.iterator(expr, context);
        String err = null;
        while (queryResults.hasNext()) {
            XdmItem xpathResult = queryResults.next();
            if (++ count < start) {
                continue;
            }
            if (count == 1 && !xpathResult.isAtomicValue()) {
                net.sf.saxon.s9api.QName name = ((XdmNode)xpathResult).getNodeName();
                if (name != null && name.getNamespaceURI().equals(EXPATH_HTTP_NS) &&
                    name.getLocalName().equals("response")) {
                    err = handleEXPathResponse(req, rsp, xpathResults, xpathResult);
                    if (queryResults.hasNext()) {
                        logger.warn ("Ignoring results following http:response, which should be the sole item in its result");
                    }
                    break;
                }
            }
            err = safeAddResult(xpathResults, xpathResult);
            if (err != null) {
                xpathResult = null;
                break;
            }
            if ((len > 0 && xpathResults.size() >= len) || 
                    (timeAllowed > 0 && (System.currentTimeMillis() - tstart) > timeAllowed)) {
                break;
            }
        }
        ArrayList<TransformerException> errors = evaluator.getErrorListener().getErrors();
        if (! errors.isEmpty()) {
            err = formatError(query, errors, evaluator.getQueryStats());
            if (xpathResults.size() == 0) {
                xpathResults = null; // throw a 400 error; don't return partial results
            }
        }
        if (err != null) {
            rsp.add ("xpath-error", err);
        }
        rsp.add("xpath-results", xpathResults);
        result.setDocList (new DocSlice(0, 0, null, null, evaluator.getQueryStats().docCount, 0));
        rb.setResult (result);
        rsp.add ("response", rb.getResults().docList);
        if (xpathResults != null) {
            if (logger.isDebugEnabled()) {
                logger.debug ("retrieved: " + ((Evaluator)evaluator).getDocReader().getCacheMisses() + " docs, " +
                    xpathResults.size() + " results, " + (System.currentTimeMillis() - tstart) + "ms");
            } 
        } else {
            logger.warn ("xquery evaluation error: " + ((Evaluator)evaluator).getDocReader().getCacheMisses() + " docs, " +
                    "0 results, " + (System.currentTimeMillis() - tstart) + "ms");
        }
    }

    private String handleEXPathResponse(SolrQueryRequest req, SolrQueryResponse rsp, NamedList<Object> xpathResults, XdmItem xpathResult) {
        XdmNode expathResponse;
        expathResponse = (XdmNode) xpathResult;
        HttpServletRequest httpReq = (HttpServletRequest) req.getContext().get("httpServletRequest");
        HttpServletResponse httpResp = (HttpServletResponse) httpReq.getAttribute("httpServletResponse");
        TinyElementImpl responseNode = (TinyElementImpl) expathResponse.getUnderlyingNode();
        // Get the status code and message
        String status = responseNode.getAttributeValue("", "status");
        String message = responseNode.getAttributeValue("", "message");
        int istatus = 200;
        if (status != null) {
            try {
                istatus = Integer.parseInt(status);
            } catch (NumberFormatException e) {
                throw new LuxException ("Non-numeric response status code: " + status);
            }
            if (istatus >= 400) {
                try {
                    if (message != null) {
                        httpResp.sendError(istatus, message);
                    } else {
                        httpResp.sendError(istatus);
                    }                            
                } catch (IOException e) {
                    logger.error("sendError failed: " + e.getMessage());
                }
            }
            // if an error is generated by the query, call sendError?
            httpResp.setStatus(istatus);
        }
        // Get the body, its charset and content-type and return the body to be used as the result
        XdmSequenceIterator children = expathResponse.axisIterator(Axis.CHILD);
        while (children.hasNext()) {
            XdmNode child = (XdmNode) children.next();
            net.sf.saxon.s9api.QName childName = child.getNodeName();
            if (! childName.getNamespaceURI().equals(EXPATH_HTTP_NS)) {
                logger.warn("ignoring unknown response child element: " + childName.getClarkName());
                continue;
            }
            if (childName.getLocalName().equals("body")) {
                // got the body
                String src = child.getAttributeValue(qnameFor("src"));
                if (src != null) {
                    throw new LuxException ("The body/@src attribute is not supported");
                }
                String characterSet = child.getAttributeValue(qnameFor("charset"));
                if (characterSet == null) {
                    characterSet = "utf-8";
                }
                String contentType = child.getAttributeValue(qnameFor("content-type"));
                if (contentType != null) {
                    contentType += "; charset=" + characterSet;
                }
                if (contentType == null) {
                    contentType = req.getParams().get("lux.contentType", contentType);
                    if (contentType != null) {
                        contentType = contentType.replaceFirst ("(?<=; charset=).*", characterSet);
                    }
                }
                if (contentType != null) {
                    req.getContext().put("lux.contentType", contentType);
                }
                XdmSequenceIterator bodyKids = child.axisIterator(Axis.CHILD);
                while (bodyKids.hasNext()) {
                    XdmNode result = (XdmNode) bodyKids.next();
                    String err = safeAddResult(xpathResults, result);
                    if (err != null) {
                        return err;
                    }
                }
            }
            else if (childName.getLocalName().equals("header")) {
                String header = child.getAttributeValue(qnameFor("name"));
                String value = child.getAttributeValue(qnameFor("value"));
                httpResp.addHeader(header, value);
            }
            else if (childName.getLocalName().equals("multipart")) {
                throw new LuxException ("Multipart HTTP responses not implemented");
            }
        }
        /*
        if (istatus >= 300 && istatus < 400) {
            httpResp.sendRedirect(location);
        }
        */
        if (expathResponse != null) {
            // TODO: pass the expathResponse to the LuxResponseWriter -- why?
            req.getContext().put("expath:response", expathResponse);
        }
        return null;
    }

    private void bindRequestVariables(ResponseBuilder rb, SolrQueryRequest req,
            XQueryExecutable expr, Compiler compiler, Evaluator evaluator,
            QueryContext context) {

        @SuppressWarnings("unchecked")
        Iterator<GlobalVariableDefinition> decls = expr.getUnderlyingCompiledQuery().getStaticContext().getModuleVariables();
        boolean hasLuxHttp = false, hasEXpathRequest = false;
        while (decls.hasNext()) {
            GlobalVariableDefinition decl = decls.next();
            StructuredQName varName = decl.getVariableQName();
            if (varName.equals(new StructuredQName("", EXPATH_HTTP_NS, "input"))) {
                hasEXpathRequest = true;
            } else if (varName.equals(new StructuredQName("",  LUX_HTTP.getNamespaceURI(), LUX_HTTP.getLocalPart()))) {
                hasLuxHttp = true;
            }
        }
        if (hasLuxHttp) {
            context.bindVariable(LUX_HTTP, buildHttpParams (evaluator, req,
                queryPath != null ? queryPath : "/xquery"
                ));
        }
        if (hasEXpathRequest) {
            try {
                context.bindVariable(new QName(EXPATH_HTTP_NS, "input", ""), buildEXPathRequest(compiler, evaluator, req));
            } catch (XPathException e) {
                throw new LuxException (e);
            } 
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

    private XdmNode buildHttpParams(Evaluator evaluator, SolrQueryRequest req, String path) {
        return (XdmNode) evaluator.build(new StringReader(buildHttpInfo(req)), path);
    }

    protected String safeAddResult (NamedList<Object> xpathResults, XdmItem item) {
        try {
            addResult (xpathResults, item);
            return null;
        } catch (SaxonApiException e) {
            return e.getMessage();
        } catch (ResourceExhaustedException e) {
            return e.getMessage();
        } catch (OutOfMemoryError e) {
            return e.getMessage();
        }
    }

    protected void addResult(NamedList<Object> xpathResults, XdmItem item) throws SaxonApiException {
        if (item.isAtomicValue()) {
            // We need to get Java primitive values that Solr knows how to marshal
            XdmAtomicValue xdmValue = (XdmAtomicValue) item;
            AtomicValue value = (AtomicValue) xdmValue.getUnderlyingValue();
            TypeHierarchy typeHierarchy = solrIndexConfig.getCompiler().getProcessor().getUnderlyingConfiguration().getTypeHierarchy();
            try {
                String typeName = value.getItemType(typeHierarchy).toString();
                Object javaValue;
                if (value instanceof DecimalValue) {
                    javaValue = ((DecimalValue) value).getDoubleValue();
                    addResultBytes (8);
                } else if (value instanceof QNameValue) {
                    javaValue = ((QNameValue) value).getClarkName();
                    addResultBytes(((String)javaValue).length() * 2); // close enough, modulo surrogates
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
                    addResultBytes (javaValue.toString().length() * 2);
                } else {
                    javaValue = Value.convertToJava(value);
                    addResultBytes (javaValue.toString().length() * 2);
                }
                // TODO hexBinary and base64Binary
                xpathResults.add (typeName, javaValue);
            } catch (XPathException e) {
                xpathResults.add (value.getPrimitiveType().getDisplayName(), value.toString());
            }
        } else {
            XdmNode node = (XdmNode) item;
            XdmNodeKind nodeKind = node.getNodeKind();
            StringWriter buf = new StringWriter ();
            // TODO: tinybin serialization!
            serializer.setOutputWriter(buf);
            serializer.serializeNode(node);
            String xml = buf.toString();
            addResultBytes (xml.length() * 2);
            xpathResults.add(nodeKind.toString().toLowerCase(), xml);
        }
    }
    
    private void addResultBytes (int count) {
        if (resultByteSize + count > MAX_RESULT_SIZE) {
            throw new ResourceExhaustedException("Maximum result size exceeded, returned result has been truncated");
        }
        resultByteSize += count;
    }
    
    // Hand-coded serialization may be a bit fragile, but the only alternative 
    // using Saxon is too inconvenient
    private String buildHttpInfo(SolrQueryRequest req) {
        StringBuilder buf = new StringBuilder();
        buf.append (String.format("<http>"));
        buf.append ("<params>");
        SolrParams params = req.getParams();
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
        Map<Object, Object> context = req.getContext();
        String webapp = (String) context.get("webapp");
        if (webapp == null) {
            webapp = "";
        }
        buf.append("<context-path>").append(webapp).append("</context-path>");
        // TODO: headers, path, etc?
        buf.append ("</http>");
        return buf.toString();
    }

    private static final String EXPATH_HTTP_NS = "http://expath.org/ns/webapp";
    
    private XdmValue buildEXPathRequest (Compiler compiler, Evaluator evaluator, SolrQueryRequest req) throws XPathException {
        LinkedTreeBuilder builder = new LinkedTreeBuilder (compiler.getProcessor().getUnderlyingConfiguration().makePipelineConfiguration());
        builder.startDocument(0);
        builder.startElement(fQNameFor("http", EXPATH_HTTP_NS, "request"), AnyType.getInstance(), 0, 0);
        builder.namespace(new NamespaceBinding("http", EXPATH_HTTP_NS), 0);
        Request requestWrapper = (Request) req.getContext().get("httpServletRequest");
        addAttribute(builder, "method", requestWrapper.getMethod());
        addAttribute(builder, "servlet", requestWrapper.getServletPath());
        HttpServletRequest httpReq = (HttpServletRequest)requestWrapper.getRequest();
        addAttribute(builder, "path", httpReq.getServletPath());
        String pathInfo = requestWrapper.getPathInfo();
        if (pathInfo != null) {
            addAttribute(builder, "path-info", pathInfo);
        }
        builder.startContent();
        
        // child elements
        
        StringBuilder buf = new StringBuilder();

        // authority
        buf.append (requestWrapper.getScheme()).
            append("://").
            append(requestWrapper.getServerName()).
            append(':').
            append (requestWrapper.getServerPort());
        String authority = buf.toString();
        addSimpleElement(builder, "authority", authority);
        
        // url
        buf.append (httpReq.getServletPath());
        if (httpReq.getQueryString() != null) {
            buf.append ('?').append(httpReq.getQueryString());
        }
        String url = buf.toString();
        addSimpleElement(builder, "url", url);
        
        // context-root
        addSimpleElement(builder, "context-root", httpReq.getContextPath());
        
        // path - just one part: we don't do any parsing of the path
        builder.startElement(fQNameFor("http", EXPATH_HTTP_NS, "path"), BuiltInAtomicType.UNTYPED_ATOMIC, 0, 0);
        builder.startContent();
        addSimpleElement(builder, "part", httpReq.getServletPath());
        builder.endElement();
        
        // params
        Iterator<String> paramNames = req.getParams().getParameterNamesIterator();
        while (paramNames.hasNext()) {
            String param = paramNames.next();
            String[] values = req.getParams().getParams(param);
            for (String value : values) {
                builder.startElement(fQNameFor("http", EXPATH_HTTP_NS, "param"), BuiltInAtomicType.UNTYPED_ATOMIC, 0, 0);
                addAttribute (builder, "name", param);
                addAttribute (builder, "value", value);
                builder.startContent();
                builder.endElement();
            }
        }
        
        // headers
        @SuppressWarnings("unchecked")
        Enumeration<String> headerNames = httpReq.getHeaderNames(); 
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            @SuppressWarnings("unchecked")
            Enumeration<String> headerValues = httpReq.getHeaders(headerName);
            while (headerValues.hasMoreElements()) {
                String value = headerValues.nextElement();
                builder.startElement(fQNameFor("http", EXPATH_HTTP_NS, "header"), BuiltInAtomicType.UNTYPED_ATOMIC, 0, 0);
                addAttribute (builder, "name", headerName);
                addAttribute (builder, "value", value);
                builder.startContent();
                builder.endElement();
            }
        }
        ArrayList<XdmItem> resultSequence = null;
        if (req.getContentStreams() != null) {
            resultSequence = new ArrayList<XdmItem>();
            handleContentStreams (builder, req, resultSequence, evaluator);
        }
        builder.endElement();           // end request
        builder.endDocument();
        XdmNode expathReq = new XdmNode (builder.getCurrentRoot());
        if (resultSequence == null) {
            return expathReq;
        }
        resultSequence.add(0, expathReq);
        return new XdmValue (resultSequence);
    }
    
    private void handleContentStreams (LinkedTreeBuilder builder, SolrQueryRequest req, ArrayList<XdmItem> result, Evaluator evaluator) throws XPathException {
        // parts
        int i = 0;
        for (ContentStream stream : req.getContentStreams()) {
            String contentType = stream.getContentType();
            //String name = stream.getName();
            byte[] partBytes = null;
            try {
                partBytes = IOUtils.toByteArray(stream.getStream(), stream.getSize()); 
            } catch (IOException e) {
                throw new LuxException (e);
            }
            String charset = ContentStreamBase.getCharsetFromContentType(contentType);
            if (charset == null) {
                charset = "utf-8";
            }
            if (!isText(contentType)) {
                throw new LuxException ("binary values not supported");
            }
            XdmItem part = null;
            if (isXML(contentType)) {
                try {
                    part = evaluator.build(new ByteArrayInputStream(partBytes), "#part" + i);
                } catch (LuxException e) {
                    // failed to parse
                    logger.warn("Caught an exception while parsing XML: " + e.getMessage() + ", treating it as plain text");
                    contentType = "text/plain; charset=" + charset;
                }
            }
            if (part == null) {
                String text;
                try {
                    text = new String (partBytes, charset);
                } catch (UnsupportedEncodingException e1) {
                    throw new LuxException (e1);
                }
                if (isHTML(contentType)) {
                    HtmlParser parser = new HtmlParser();
                    //Parser parser = new Parser();
                    SAXSource source = new SAXSource (parser, new InputSource (new StringReader (text)));
                    try {
                        part = evaluator.getDocBuilder().build(source);
                    } catch (SaxonApiException e) {
                        e.printStackTrace();
                        logger.warn ("failed to parse HTML; treating as plain text: " + e.getMessage());
                    }
                }
                if (part == null) {
                    part = new XdmNode (new TextFragmentValue(text, "#part" + i));
                }
            }
            result.add (part);
            builder.startElement(fQNameFor("http", EXPATH_HTTP_NS, "body"), BuiltInAtomicType.UNTYPED_ATOMIC, 0, 0);
            addAttribute(builder, "position", "1");
            addAttribute(builder, "content-type", contentType);
            builder.startContent();
            builder.endElement();
        }
    }
    
    private boolean isText (String contentType) {
        return contentType.startsWith("text/") || isHTML(contentType) || isXML(contentType);
    }

    private boolean isHTML (String contentType) {
        return contentType.matches(".*/html($| )");
    }

    private boolean isXML (String contentType) {
        return  contentType.matches(".*/xml($| )") ||
                contentType.matches(".*\\+xml($| )");
    }

    private void addSimpleElement(LinkedTreeBuilder builder, String name, String text)
            throws XPathException {
        builder.startElement (fQNameFor("http", EXPATH_HTTP_NS, name), BuiltInAtomicType.STRING, 0, 0);
        builder.startContent();
        builder.characters(text, 0, 0);
        builder.endElement();
    }

    private void addAttribute(LinkedTreeBuilder builder, String name, String value)
            throws XPathException {
        builder.attribute(fQNameFor("", "", name), BuiltInAtomicType.UNTYPED_ATOMIC, value, 0, 0);
    }
    
    public SolrIndexConfig getSolrIndexConfig() {
        return solrIndexConfig;
    }
    
    // TODO cache these
    protected FingerprintedQName fQNameFor (String prefix, String namespace, String name) {
        return new FingerprintedQName(prefix, namespace, name);
    }
    
    protected net.sf.saxon.s9api.QName qnameFor (String namespace, String localName) {
        return new net.sf.saxon.s9api.QName (namespace, localName);
    }

    protected net.sf.saxon.s9api.QName qnameFor (String localName) {
        return new net.sf.saxon.s9api.QName (localName);
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

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

