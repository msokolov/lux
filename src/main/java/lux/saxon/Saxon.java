package lux.saxon;

import java.io.IOException;
import java.io.Reader;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamSource;

import lux.api.Evaluator;
import lux.api.Expression;
import lux.api.LuxException;
import lux.api.QueryContext;
import lux.api.ResultSet;
import lux.compiler.PathOptimizer;
import lux.functions.FieldTerms;
import lux.functions.LuxCount;
import lux.functions.LuxExists;
import lux.functions.LuxSearch;
import lux.index.XmlIndexer;
import lux.index.field.XmlField;
import lux.search.LuxSearcher;
import lux.xml.XmlBuilder;
import lux.xpath.AbstractExpression;
import lux.xpath.FunCall;
import lux.xquery.XQuery;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.CollectionURIResolver;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.XPathException;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of a lux query/expression evaluator using Saxon
 *
 */
public class Saxon extends Evaluator implements URIResolver, CollectionURIResolver {

    private Processor processor;
    public Processor getProcessor() {
        return processor;
    }

    private XQueryCompiler xqueryCompiler;
    private XPathCompiler xpathCompiler;
    private SaxonBuilder saxonBuilder;
    private SaxonTranslator translator;
    private PathOptimizer optimizer;
    private TransformErrorListener errorListener;
    
    private CachingDocReader docReader;
    
    private Config config;
    
    private final Dialect dialect;
    
    private CollectionURIResolver defaultCollectionURIResolver;
    
    private boolean enableLuxOptimization = true;
    
    private Logger logger;
    
    public Saxon(LuxSearcher searcher, XmlIndexer indexer, Dialect dialect) {
        super (searcher, indexer);
        config = new Config(this);
        config.setDocumentNumberAllocator(new DocIDNumberAllocator());
        config.setConfigurationProperty(FeatureKeys.XQUERY_PRESERVE_NAMESPACES, false);
        defaultCollectionURIResolver = config.getCollectionURIResolver();
        config.setCollectionURIResolver(this);
        config.setErrorListener(new TransformErrorListener());
        processor = new Processor (config);
        processor.registerExtensionFunction(new LuxSearch());
        processor.registerExtensionFunction(new LuxCount());
        processor.registerExtensionFunction(new LuxExists());
        processor.registerExtensionFunction(new FieldTerms());
        saxonBuilder = new SaxonBuilder();
        translator = new SaxonTranslator(config);
        if (indexer != null) {
            optimizer = new PathOptimizer(getIndexer());
        }
        invalidateCache();
        this.dialect = dialect;
        logger = LoggerFactory.getLogger(getClass());
        errorListener = new TransformErrorListener();
    }
    
    /**
     * Creates a Saxon query evaluator with no searcher or indexer using the supplied 
     * dialect.  A searcher and indexer must be supplied in order to compile or evaluate queries. 
     * This constructor is used when the searcher is not available until query evaluation time.
     */
    public Saxon (Dialect dialect) {
        this (null, null, dialect);
    }
    
    public enum Dialect {
        XPATH_1,
        XPATH_2,
        XQUERY_1
    }
    
    /**
     * implements URIResolver so as to resolve uris in service of fn:doc()
     */
    public Source resolve(String href, String base) throws TransformerException {
        String path = href.startsWith("lux:/") ? href.substring(5) : href;
        path = path.replace('\\', '/');
        try {
            DocIdSetIterator disi = getSearcher().search(new TermQuery(new Term(XmlField.URI.getName(), href)));
            int docID = disi.nextDoc();
            if (docID == DocIdSetIterator.NO_MORE_DOCS) {
                throw new TransformerException("document '" +  href + "' not found");
            }
            XdmNode doc = getDocReader().get(docID);
            return doc.asSource(); 
        } catch (IOException e) {
            throw new TransformerException(e);
        }
    }

    /**
     * implements CollectionURIResolver so as to resolve uris in service of fn:collection().
     * For now, all collections retrieve all documents.  TODO: implement directory-based collections.
     */
    public SequenceIterator<?> resolve(String href, String base, XPathContext context) throws XPathException {
        if (href == null || href.startsWith("lux:")) {
            //String path = href.substring(5);
            //path = path.replace('\\', '/');
            return new LuxSearch().iterate(new MatchAllDocsQuery(), this, 0);
        }
        return defaultCollectionURIResolver.resolve(href, base, context);
    }
        
    @Override
    public SaxonExpr compile(String exprString) throws LuxException {
        switch (dialect) {
        case XPATH_1: case XPATH_2:
            return compileXPath(exprString);
        case XQUERY_1:
            return compileXQuery(exprString);
        default:
            throw new LuxException ("Unsupported query dialect: " + dialect);
        }
    }

    private SaxonExpr compileXPath(String exprString) throws LuxException {
        XPathExecutable xpath;
        try {
            xpath = getXPathCompiler().compile(exprString);
        } catch (SaxonApiException e) {
            throw new LuxException ("Syntax error compiling: " + exprString, e);
        }
        if (! isEnableLuxOptimization()) {
            return new SaxonExpr (xpath, null);
        }        
        AbstractExpression expr = translator.exprFor(xpath.getUnderlyingExpression().getInternalExpression());
        AbstractExpression optimizedExpr = optimizer.optimize(expr);
        try {
             xpath = getXPathCompiler().compile(optimizedExpr.toString());
        } catch (SaxonApiException e) {
            throw new LuxException ("Syntax error compiling: " + optimizedExpr.toString(), e);
        }
        logger.debug("optimized xpath: " + optimizedExpr.toString());
        return new SaxonExpr(xpath, optimizedExpr);
    }
    
    private SaxonExpr compileXQuery(String exprString) throws LuxException {
        XQueryExecutable xquery;
        try {
            xquery = getXQueryCompiler().compile(exprString);
        } catch (SaxonApiException e) {
            throw new LuxException (e);
        }
        if (! isEnableLuxOptimization()) {
            return new SaxonExpr (xquery, null);
        }
        XQuery abstractQuery = translator.queryFor (xquery);
        XQuery optimizedQuery = optimizer.optimize(abstractQuery);
        try {
            xquery = getXQueryCompiler().compile(optimizedQuery.toString());
        } catch (SaxonApiException e) {
            throw new LuxException (e);
        }
        logger.debug("optimized xquery: " + optimizedQuery.toString());
        return new SaxonExpr(xquery, optimizedQuery);
    }

    @Override
    public ResultSet<?> evaluate(Expression expr) {
        return evaluate (expr, null);
    }
    
    @Override
    public ResultSet<?> evaluate(Expression expr, QueryContext context) {
        return iterate (expr, context);
    }

    @Override
    public ResultSet<?> iterate(Expression expr, QueryContext context) { 
        SaxonExpr saxonExpr = (SaxonExpr) expr;
        if (context == null) {
            context = new QueryContext();
        }
        context.setEvaluator(this);
        try {
            return saxonExpr.evaluate(context);
        } finally {
            docReader.clear();
        }
    }

    public SaxonBuilder getBuilder() {
        return saxonBuilder;
    }
    
    public Config getConfig() {
        return config;
    }
    
    // FIXME - merge with lux.xml.SaxonBuilder or rename...
    public class SaxonBuilder extends XmlBuilder {
        private DocumentBuilder documentBuilder;

        SaxonBuilder () {
            documentBuilder = processor.newDocumentBuilder();
            documentBuilder.setDTDValidation(false);
        }
        
        // TODO: change to setNextDocID() followed by call to standard build()
        public XdmNode build (Reader reader, String uri, int docID) {
            config.getDocumentNumberAllocator().setNextDocID(docID);
            XdmNode xdmNode = (XdmNode) build(reader, uri);
            return xdmNode;
        }

        @Override
        public Object build(Reader reader, String uri) {
            try {
                return documentBuilder.build(new StreamSource (reader, uri));
            } catch (SaxonApiException e) {
               throw new LuxException(e);
            }
        }
    }

    public SaxonTranslator getTranslator() {
        return translator;
    }

    public CachingDocReader getDocReader() {
        return docReader;
    }    
    
    public void declareNamespace (String prefix, String namespace) {
        switch (dialect) {
        case XPATH_1: case XPATH_2:
            getXPathCompiler().declareNamespace(prefix, namespace);
            break;
        case XQUERY_1:
            getXQueryCompiler().declareNamespace(prefix, namespace);
            break;
        default:
            break;
        }
    }
    
    public XQueryCompiler getXQueryCompiler () {
        if (xqueryCompiler == null) {
            xqueryCompiler = processor.newXQueryCompiler();
            xqueryCompiler.declareNamespace("lux", FunCall.LUX_NAMESPACE);
            xqueryCompiler.setErrorListener(errorListener);
        }
        errorListener.clear();
        return xqueryCompiler;
    }
        
    public XPathCompiler getXPathCompiler () {
        if (xpathCompiler == null) {
            xpathCompiler = processor.newXPathCompiler();
            xpathCompiler.declareNamespace("lux", FunCall.LUX_NAMESPACE);
            xpathCompiler.declareNamespace("fn", FunCall.FN_NAMESPACE);
        }
        return xpathCompiler;
    }
    
    @Override 
    public void setSearcher (LuxSearcher searcher) {
        super.setSearcher(searcher);
        invalidateCache();
    }

    public void invalidateCache() {
        if (getSearcher() != null && getIndexer() != null) {
            docReader = new CachingDocReader(getSearcher().getIndexReader(), getBuilder(), getIndexer());
        } else {
            docReader = null;
        }
    }

    @Override 
    public void setIndexer(XmlIndexer indexer) {
        super.setIndexer(indexer);
        invalidateCache();
        optimizer = new PathOptimizer(getIndexer());
    }

    public boolean isEnableLuxOptimization() {
        return enableLuxOptimization;
    }

    public void setEnableLuxOptimization(boolean enableLuxOptimization) {
        this.enableLuxOptimization = enableLuxOptimization;
    }

    public PathOptimizer getOptimizer() {
        return optimizer;
    }
    
    public TransformErrorListener getErrorListener () {
        return errorListener;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
