package lux;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

import lux.exception.LuxException;
import lux.exception.NotFoundException;
import lux.functions.Search;
import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
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
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.slf4j.LoggerFactory;

/**
 * This class holds all the per-request state required for evaluating queries. It is *not* thread-safe.
 */
public class Evaluator {

    public static final String LUX_NAMESPACE = "http://luxdb.net";
    
    private final Compiler compiler;
    private final CachingDocReader docReader;
    private final DocWriter docWriter;
    private final DocumentBuilder builder;
    private final TransformErrorListener errorListener;

    private LuxSearcher searcher;
    private LuxQueryParser queryParser;
    private XmlQueryParser xmlQueryParser;
    private QueryStats queryStats;
    private URIResolver defaultURIResolver;

    /**
     * Creates an evaluator that uses the provided objects to evaluate queries.
     * @param compiler queries are compiled using this
     * @param searcher search operations required by evaluated queries are carried out using this
     * @param docWriter this writer is used to modify the index (write, delete documents).  It must 
     * be tied to the same index as the searcher.
     */
    public Evaluator(Compiler compiler, LuxSearcher searcher, DocWriter docWriter) {
        LoggerFactory.getLogger(getClass()).debug("new evaluator");
        this.compiler = compiler;
        this.searcher = searcher;
        Configuration config = compiler.getProcessor().getUnderlyingConfiguration();
        defaultURIResolver = config.getURIResolver();
        config.setURIResolver(new LuxURIResolver());
        config.setCollectionURIResolver(new LuxCollectionURIResolver());
        config.setOutputURIResolver(new LuxOutputURIResolver());
        builder = compiler.getProcessor().newDocumentBuilder();
        if (searcher != null) {
            docReader = new CachingDocReader(builder, config, compiler.getIndexConfiguration());
        } else {
            docReader = null;
        }
        this.docWriter = docWriter;
        queryStats = new QueryStats();
        errorListener = new TransformErrorListener();
        errorListener.setUserData(this);
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
    	Evaluator eval = new Evaluator (compiler, searcher, writer);
    	return eval;
    }
    
    /**
     * Creates a query evaluator that has no association with an index (no searcher or writer).  
     * A searcher must be supplied in order to compile or evaluate queries that are optimized for use with a Lux (Lucene) index. 
     * This constructor is used in testing, or may be useful for compiling and executing queries that don't
     * rely on documents stored in Lucene.
     */
    public Evaluator () {
        this (new Compiler(IndexConfiguration.DEFAULT), null, null);
    }
    
    /** Call this method to release the Evaluator from its role as the URI and Collection URI
     * Resolver, and to close the underlying Lucene Searcher.
     */
    public void close() {
        LoggerFactory.getLogger(getClass()).debug("close evaluator");
        Configuration config = compiler.getProcessor().getUnderlyingConfiguration();
        config.setURIResolver(defaultURIResolver);
        config.setCollectionURIResolver(null);
        try {
            searcher.close();
            docWriter.close();
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
        XQueryExecutable compiledQuery = compiler.compile(query, errorListener);
        if (queryStats != null && compiler.getLastOptimized() != null) {
            queryStats.optimizedQuery = compiler.getLastOptimized().toString();
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
     * @param listener an error listener that will capture errors and also act as a conduit that passes
     * the Evaluator to function calls that require it.
     * @return the results of the evaluation; any errors are encapsulated in the result set.
     */
    private XdmResultSet evaluate(XQueryExecutable xquery, QueryContext context, TransformErrorListener listener) { 
        if (context == null) {
            context = new QueryContext();
        }
        XQueryEvaluator xqueryEvaluator = xquery.load();
        try {
            listener.setUserData(this);
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
            XdmValue value = xqueryEvaluator.evaluate();
            return new XdmResultSet (value);
        } catch (SaxonApiException e) {
            return new XdmResultSet(((TransformErrorListener)xqueryEvaluator.getErrorListener()).getErrors());
        } finally {
            if (docReader != null) {
                docReader.clear();
            }
            // TODO: get a new reader form the docWriter (for Lucene direct writer only) to enable
            // auto-commit via NRT
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
    public XdmNode build (Reader xml, String uri) {
        StreamSource source = new StreamSource(xml);
        source.setSystemId(uri);
        try {
            return builder.build(source);
        } catch (SaxonApiException e) {
            throw new LuxException (e);
        }
    }
    
    public class LuxURIResolver implements URIResolver {
        /**
         * Evaluator provides this method as an implementation of URIResolver so as to resolve uris in service of fn:doc().
         * file: uri resolution is delegated to the default resolver by returning null.  lux: and other uris are all resolved
         * using the provided searcher.  The lux: prefix is optional, e.g: the uris "lux:/hello.xml" and "/hello.xml"
         * are equivalent.  Documents read from the index are numbered according to their Lucene docIDs, and retrieved
         * using the {@link CachingDocReader}.
         * @throws IllegalStateException if a search is attempted, but no searcher was provided
         * @throws TransformerException if the document is not found in the index, or there was an IOException
         * thrown by Lucene.
         */
        @Override
        public Source resolve(String href, String base) throws TransformerException {
            boolean isFile;
            String path = href;
            if (href.matches("^\\w+:.*$")) {
                isFile = href.startsWith("file:");
                if (isFile) {
                    path = href.substring(5);
                } else if (href.startsWith("lux:/")) {
                    path = href.substring(5);
                }
            } else {
                // relative url, look at base
                if (base != null) {
                    isFile = base.startsWith("file:");
                } else {
                    isFile = false;
                }
            }
            if (isFile) {
                Source source = null;
                // let the default resolver do its thing
                if (defaultURIResolver != null) {
                    source = defaultURIResolver.resolve(path, base);
                }
                if (source == null) {
                    source = getCompiler().getProcessor().getUnderlyingConfiguration().getSystemURIResolver().resolve (path, base);
                }
                return source;
            }
            if (searcher == null) {
                throw new IllegalStateException ("Attempted search, but no searcher was provided");
            }
            path = path.replace('\\', '/');
            try {
                DocIdSetIterator disi = getSearcher().search(new TermQuery(new Term(compiler.getUriFieldName(), path)));
                int docID = disi.nextDoc();
                if (docID == DocIdSetIterator.NO_MORE_DOCS) {
                    throw new NotFoundException(href);
                }
                XdmNode doc = docReader.get(docID, getSearcher().getIndexReader());
                return doc.asSource(); 
            } catch (IOException e) {
                throw new TransformerException(e);
            }
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
                return new Search().iterate(new MatchAllDocsQuery(), Evaluator.this, 0, null, 1);
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

                return new Search().iterate(q, Evaluator.this, 0, null, 1);
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
            searcher = new LuxSearcher (DirectoryReader.openIfChanged((DirectoryReader) getSearcher().getIndexReader()));
            current.close();
        } catch (IOException e) {
            throw new LuxException (e);
        }
    }
    
    public Compiler getCompiler() {
        return compiler;
    }

    public CachingDocReader getDocReader () {
        return docReader;
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
            FieldDefinition field = config.getField(FieldName.ELEMENT_TEXT);
            xmlQueryParser = new XmlQueryParser(config.getFieldName(field), field.getAnalyzer());
        }
        return xmlQueryParser;
    }
    
    /**
     * @return the error listener that receives static and dynamic error events. 
     */
    public TransformErrorListener getErrorListener() {
        return errorListener;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
