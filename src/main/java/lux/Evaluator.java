package lux;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Map;

import javax.xml.transform.Result;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;

import lux.exception.LuxException;
import lux.functions.Search;
import lux.index.FieldRole;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.index.analysis.DefaultAnalyzer;
import lux.index.field.FieldDefinition;
import lux.query.parser.LuxQueryParser;
import lux.query.parser.XmlQueryParser;
import lux.search.LuxSearcher;
import lux.xml.QName;
import net.sf.saxon.Configuration;
import net.sf.saxon.event.ProxyReceiver;
import net.sf.saxon.event.Receiver;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.CollectionURIResolver;
import net.sf.saxon.lib.OutputURIResolver;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.SaxonApiUncheckedException;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.slf4j.LoggerFactory;

/**
 * This class holds all the per-request state required for evaluating queries. It is *not* thread-safe.
 */
public class Evaluator {

    public static final String LUX_NAMESPACE = "http://luxdb.net";
    
    final Compiler compiler;
    final CachingDocReader docReader;
    private final DocWriter docWriter;
    private final DocumentBuilder builder;
    private final TransformErrorListener errorListener;

    LuxSearcher searcher;
    private LuxQueryParser queryParser;
    private XmlQueryParser xmlQueryParser;
    private QueryStats queryStats;
    private QueryContext queryContext;

    /**
     * Creates an evaluator that uses the provided objects to evaluate queries.
     * @param compiler queries are compiled using this
     * @param searcher search operations required by evaluated queries are carried out using this
     * @param docWriter this writer is used to modify the index (write, delete documents).  It must 
     * be tied to the same index as the searcher.
     */
    public Evaluator(Compiler compiler, LuxSearcher searcher, DocWriter docWriter) {
        this.compiler = compiler;
        this.searcher = searcher;
        builder = compiler.getProcessor().newDocumentBuilder();
        Configuration config = compiler.getProcessor().getUnderlyingConfiguration();
        if (searcher != null) {
            docReader = new CachingDocReader(builder, config, compiler.getIndexConfiguration());
        } else {
            docReader = null;
        }
        this.docWriter = docWriter;
        queryStats = new QueryStats();
        errorListener = new TransformErrorListener();
        errorListener.setUserData(this);
        // TODO: move these out of here; they should be one-time setup for the Processor 
        config.setCollectionURIResolver(new LuxCollectionURIResolver());
        config.setOutputURIResolver(new LuxOutputURIResolver());
        if (config.getURIResolver() == null || !(config.getURIResolver() instanceof LuxURIResolver)) {
            config.setURIResolver(new LuxURIResolver(config.getSystemURIResolver(), this, 
                    compiler.getIndexConfiguration().getFieldName(FieldRole.URI)));
        }
    }
    
    /**
     * Creates a query evaluator that searches and writes to the given Directory (Lucene index).
     * The Directory is opened and locked; the Evaluator must be closed when it is no longer in use.
     * @param dir the directory where documents are to be searched, store and retrieved  
     * @return the new Evaluator
     * @throws IOException if the Directory cannot be opened 
     */
    public static Evaluator createEvaluator (Directory dir) throws IOException {
    	XmlIndexer indexer = new XmlIndexer();
    	IndexWriter indexWriter = indexer.newIndexWriter(dir);
    	DirectDocWriter writer = new DirectDocWriter(indexer, indexWriter);
    	Compiler compiler = new Compiler(indexer.getConfiguration());
    	LuxSearcher searcher = new LuxSearcher(DirectoryReader.open(indexWriter, true));
    	return new Evaluator (compiler, searcher, writer);
    }
    
    /**
     * Creates a query evaluator that has no association with an index (no searcher or writer).  
     * A searcher must be supplied in order to compile or evaluate queries that are optimized for use with a Lux (Lucene) index. 
     * This constructor is used in testing, or may be useful for compiling and executing queries that don't
     * rely on documents stored in Lucene.
     */
    public Evaluator () {
        this (new Compiler(new IndexConfiguration()), null, null);
    }
    
    /** Call this method to release the Evaluator from its role as the URI and Collection URI
     * Resolver, and to close the underlying Lucene Searcher.
     */
    public void close() {
        LoggerFactory.getLogger(getClass()).debug("close evaluator");
        Configuration config = compiler.getProcessor().getUnderlyingConfiguration();
        config.setURIResolver(null);
        config.setCollectionURIResolver(null);
        try {
            searcher.close();
            docWriter.close(this);
        } catch (IOException e) {
            LoggerFactory.getLogger (getClass()).error ("failed to close searcher", e);
            e.printStackTrace();
        }
    }

    /**
     * Compile and evaluate the given query, as XQuery, with no context defined.
     * @param query an XQuery expression (main module)
     * @return the results of the evaluation; any errors are encapsulated in the result set.
     */
    public XdmResultSet evaluate(String query) {
        return evaluate (query, null);
    }
    
    public XdmResultSet evaluate(String query, QueryContext context) {
        errorListener.clear();
        XQueryExecutable compiledQuery;
        if (searcher == null) {
            // don't optimize the query for use w/indexes when we have none
            try {
                compiledQuery = compiler.getXQueryCompiler().compile(query);
            } catch (SaxonApiException e) {
                throw new LuxException (e);
            }
        } else {
            compiledQuery = compiler.compile(query, errorListener, queryStats);
        }
        return evaluate (compiledQuery, context);
    }
    
    /**
     * Evaluate the already-compiled query, with no context defined.
     * @param xquery a compiled XQuery expression
     * @return the results of the evaluation; any errors are encapsulated in the result set.
     */
    public XdmResultSet evaluate(XQueryExecutable xquery) {
        return evaluate (xquery, null);
    }
    
    public XdmResultSet evaluate(XQueryExecutable xquery, QueryContext context) {
        return evaluate (xquery, context, errorListener);
    }

    /**
     * Evaluate the already-compiled query, with the given context (external variable bindings, context item) defined.
     * @param xquery a compiled XQuery expression
     * @param context the query context holds external variable bindings and the context item
     * @param listener an error listener that will capture errors and also (cough, hack, spew)
     * act as a conduit that passes the Evaluator to function calls that require it.
     * @return the results of the evaluation; any errors are encapsulated in the result set.
     */
    private XdmResultSet evaluate(XQueryExecutable xquery, QueryContext context, TransformErrorListener listener) { 
        if (context == null) {
            context = new QueryContext();
        }
        XQueryEvaluator xqueryEvaluator = prepareEvaluation(context, listener, xquery);
        try {
            listener.setUserData(this);
            xqueryEvaluator.setErrorListener(listener);
            xqueryEvaluator.setContextItem((XdmItem) context.getContextItem());
            if (context.getVariableBindings() != null) {
                for (Map.Entry<QName, Object> binding : context.getVariableBindings().entrySet()) {
                    net.sf.saxon.s9api.QName saxonQName = new net.sf.saxon.s9api.QName(binding.getKey());
                    xqueryEvaluator.setExternalVariable(saxonQName, (XdmValue) binding.getValue());
                }
            }
            XdmValue value = xqueryEvaluator.evaluate();
            return new XdmResultSet (value);
        } catch (SaxonApiException e) {
            return new XdmResultSet(((TransformErrorListener)xqueryEvaluator.getErrorListener()).getErrors());
        } finally {
            if (docReader != null) {
                docReader.clear();
            }
            // TODO: get a new reader from the docWriter (for Lucene direct writer only) to enable
            // auto-commit via NRT
        }
    }
    
    /**
     * Evaluate the already-compiled query
     * @param xquery a compiled XQuery expression
     * @param context the dynamic query context
     * @return an iterator over the results of the evaluation.
     */
    public Iterator<XdmItem> iterator(XQueryExecutable xquery, QueryContext context) {
        return iterator (xquery, context, errorListener);
    }
    
    private Iterator<XdmItem> iterator(XQueryExecutable xquery, QueryContext context, TransformErrorListener listener) { 
        if (context == null) {
            context = new QueryContext();
        }
        XQueryEvaluator xqueryEvaluator = prepareEvaluation(context, listener, xquery);
        try {
            return xqueryEvaluator.iterator();
        } catch (SaxonApiUncheckedException e) {
            return new XdmResultSet(((TransformErrorListener)xqueryEvaluator.getErrorListener()).getErrors()).iterator();
        }
    }

    private XQueryEvaluator prepareEvaluation(QueryContext context, TransformErrorListener listener, XQueryExecutable xquery) {
        listener.setUserData(this);
        this.queryContext = context;
        XQueryEvaluator xqueryEvaluator = xquery.load();
        xqueryEvaluator.setErrorListener(listener);
        if (context != null) {
            xqueryEvaluator.setContextItem((XdmItem) context.getContextItem());
            if (context.getVariableBindings() != null) {
                for (Map.Entry<QName, Object> binding : context.getVariableBindings().entrySet()) {
                    net.sf.saxon.s9api.QName saxonQName = new net.sf.saxon.s9api.QName(binding.getKey());
                    xqueryEvaluator.setExternalVariable(saxonQName, (XdmValue) binding.getValue());
                }
            }
        }
        return xqueryEvaluator;
    }
    
    /**
     * Build a document as a Saxon {@link XdmNode}.  The document will be given a generated id outside
     * the space of ids reserved for indexed documents.
     * @param xml the document content
     * @param uri the document uri
     * @return the constructed document
     * @throws LuxException if any error occurs (such as an XML parse error).
     */
    public XdmNode build (Reader xml, String uri) {
        StreamSource source = new StreamSource(xml);
        source.setSystemId(uri);
        try {
            return builder.build(source);
        } catch (SaxonApiException e) {
            throw new LuxException (e);
        }
    }
    
    /**
     * Build a document as a Saxon {@link XdmNode}.  The document will be given a generated id outside
     * the space of ids reserved for indexed documents.
     * @param xml the document content
     * @param uri the document uri
     * @return the constructed document
     * @throws LuxException if any error occurs (such as an XML parse error).
     */
    public XdmNode build (InputStream xml, String uri) {
        StreamSource source = new StreamSource(xml);
        source.setSystemId(uri);
        try {
            return builder.build(source);
        } catch (SaxonApiException e) {
            throw new LuxException (e);
        }
    }
    
    public class LuxCollectionURIResolver implements CollectionURIResolver {
        /**
         * Evaluator provides this method as an implementation of CollectionURIResolver in support of fn:collection() (and fn:uri-collection()).
         * @param href the path to resolve.  If empty or null, all documents are returned (from the index).  Paths beginning "lux:" are parsed
         * (after removing the prefix) using {@link LuxQueryParser} and evaluated as queries against the index.  Other paths
         * are resolved using the default resolver.
         * @param base the base uri of the calling context (see {@link CollectionURIResolver}).  This is ignored for lux queries.
         */
        @Override
        public SequenceIterator<?> resolve(String href, String base, XPathContext context) throws XPathException {
            if (StringUtils.isEmpty(href)) {
                return new Search().iterate(new MatchAllDocsQuery(), Evaluator.this, null, 1);
            }
            if (href.startsWith("lux:")) {
                // Saxon doesn't actually enforce that this is a valid URI, and we don't care about that either
                String query = href.substring(4);
                Query q;
                try {
                    q = getLuxQueryParser().parse(query);
                } catch (ParseException e) {
                    throw new XPathException ("Failed to parse query: " + query, e);
                }
                LoggerFactory.getLogger(getClass()).debug("executing query: {}", q);

                return new Search().iterate(q, Evaluator.this, null, 1);
            }
            return compiler.getDefaultCollectionURIResolver().resolve(href, base, context);
        }
        
    }
    
    class LuxOutputURIResolver implements OutputURIResolver {
        
        class XdmDestinationProxy extends ProxyReceiver {
            private XdmDestination dest;
            public XdmDestinationProxy(Receiver nextReceiver, XdmDestination dest) {
                super(nextReceiver);
                this.dest = dest;
            }
            
        }

        @Override
        public Result resolve(String href, String base) throws TransformerException {
            try {
                XdmDestination dest = new XdmDestination();
                URI uri = new URI("lux:/").resolve(href);
                dest.setBaseURI(uri);
                Configuration config = getCompiler().getProcessor().getUnderlyingConfiguration();
                Receiver receiver = dest.getReceiver(config);
                receiver.setSystemId(href);
                XdmDestinationProxy xdmDestinationProxy = new XdmDestinationProxy(receiver, dest);
                xdmDestinationProxy.setSystemId(href);
                return xdmDestinationProxy;
            } catch (SaxonApiException e) {
                throw new TransformerException(e);
            } catch (URISyntaxException e) {
                throw new TransformerException(e);
            }
        }

        @Override
        public void close(Result result) throws TransformerException {
            XdmDestinationProxy receiver = (XdmDestinationProxy) result;
            if (docWriter == null) {
            	throw new TransformerException ("Attempted to write document " + receiver.getSystemId() + " to a read-only Evaluator");
            }
            docWriter.write(receiver.dest.getXdmNode().getUnderlyingNode(), receiver.getSystemId());
        }

        @Override
        public LuxOutputURIResolver newInstance() {
            // we have no state, so it's OK to return the same instance
            return this;
        }
        
    }
    
    /**
     * reopen the searcher so it sees any updates.
     * Do NOT call this when operating within Solr: it interferes with Solr's management
     * of open searchers/readers.
     */
    public void reopenSearcher() {
        LoggerFactory.getLogger(getClass()).debug("evaluator reopen searcher");
        try {
            LuxSearcher current = searcher;
            if (current != null) {
                searcher = new LuxSearcher (DirectoryReader.openIfChanged((DirectoryReader) current.getIndexReader()));
                current.close();
            }
            resetURIResolver ();
        } catch (IOException e) {
            throw new LuxException (e);
        }
    }
    
    private void resetURIResolver () {
        Configuration config = compiler.getProcessor().getUnderlyingConfiguration();
        config.setURIResolver(new LuxURIResolver(config.getSystemURIResolver(), this, compiler.getUriFieldName()));
    }
    
    public Compiler getCompiler() {
        return compiler;
    }

    public CachingDocReader getDocReader () {
        return docReader;
    }
    
    public DocumentBuilder getDocBuilder () {
        return builder;
    }
    
    public DocWriter getDocWriter() {
        return docWriter;
    }
    
    public LuxSearcher getSearcher() {
        return searcher;
    }    

    public QueryStats getQueryStats() {
        return queryStats;
    }

    public void setQueryStats(QueryStats queryStats) {
        this.queryStats = queryStats;        
    }

    /**
     * @return a new parser, which will be cached for re-use, or the cached parser
     */
    public LuxQueryParser getLuxQueryParser() {
        if (queryParser == null) {
            queryParser = LuxQueryParser.makeLuxQueryParser(compiler.getIndexConfiguration());
        }
        return queryParser;
    }

    /**
     * @return a new parser, which will be cached for re-use, or the cached parser
     */
    public XmlQueryParser getXmlQueryParser () {
        if (xmlQueryParser == null) {
            IndexConfiguration config = compiler.getIndexConfiguration();
            FieldDefinition field = config.getField(FieldRole.XML_TEXT);
            if (field != null) {
                Analyzer analyzer = field.getQueryAnalyzer(); 
                if (analyzer == null) {
                    analyzer = field.getAnalyzer(); 
                }
                xmlQueryParser = new XmlQueryParser(field.getName(), analyzer);
            } else {
                xmlQueryParser = new XmlQueryParser("", new DefaultAnalyzer());
            }
        }
        return xmlQueryParser;
    }
    
    /**
     * @return the error listener that receives static and dynamic error events. 
     */
    public TransformErrorListener getErrorListener() {
        return errorListener;
    }

    /**
     * @return the context associated with this query; wraps the variable bindings, namespace declarations,
     * and the Solr XQueryComponent if this is a distributed query running via SolrCloud.
     */
    public QueryContext getQueryContext() {
        return queryContext;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
