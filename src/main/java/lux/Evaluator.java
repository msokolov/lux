package lux;

import java.io.IOException;
import java.util.Map;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;

import lux.XCompiler.Dialect;
import lux.exception.LuxException;
import lux.functions.Search;
import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.index.field.FieldDefinition;
import lux.query.parser.LuxQueryParser;
import lux.query.parser.XmlQueryParser;
import lux.search.LuxSearcher;
import lux.xml.QName;
import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.CollectionURIResolver;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;

import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.slf4j.LoggerFactory;

/**
 * This class holds all the per-request state required for evaluating queries.
 */
public class Evaluator implements URIResolver, CollectionURIResolver {

    private final XCompiler compiler;
    private final DocBuilder saxonBuilder;
    private final CachingDocReader docReader;
    private final DocWriter docWriter;
    private LuxSearcher searcher;
    private LuxQueryParser queryParser;
    private XmlQueryParser xmlQueryParser;
    private QueryStats queryStats;    

    public Evaluator(XCompiler compiler, LuxSearcher searcher, DocWriter docWriter) {
        this.compiler = compiler;
        this.searcher = searcher;
        Configuration config = compiler.getProcessor().getUnderlyingConfiguration();
        config.setURIResolver(this);
        config.setCollectionURIResolver(this);
        saxonBuilder = new DocBuilder((DocIDNumberAllocator) config.getDocumentNumberAllocator(), compiler.getProcessor().newDocumentBuilder());
        if (searcher != null) {
            docReader = new CachingDocReader(saxonBuilder, compiler.getIndexConfiguration());
        } else {
            docReader = null;
        }
        this.docWriter = docWriter;
        queryStats = new QueryStats();
        // FIXME: this introduces a point of contention that prevents multiple Evaluators
        // from sharing a single XCompiler.
    }
    
    /**
     * Creates a Saxon query evaluator with no searcher.  A searcher must be supplied in order to 
     * compile or evaluate queries that are optimized for use with a Lux (Lucene) index. 
     * This constructor is used in testing, or may be useful for compiling and executing queries that don't
     * rely on documents stored in Lucene.
     */
    public Evaluator () {
        this (new XCompiler(IndexConfiguration.DEFAULT), null, null);
    }
    
    public Evaluator (Dialect dialect) {
        this();
        if (dialect != Dialect.XQUERY_1) {
            throw new LuxException ("unsupported dialect: " + dialect);
        }
    }
    
    /**
     * implements URIResolver so as to resolve uris in service of fn:doc()
     */
    public Source resolve(String href, String base) throws TransformerException {
        if (href.startsWith("file:")) {
            // let the default resolver do its thing
            return null;
        }
        String path = href.startsWith("lux:/") ? href.substring(5) : href;
        path = path.replace('\\', '/');
        try {
            DocIdSetIterator disi = getSearcher().search(new TermQuery(new Term(compiler.getUriFieldName(), href)));
            int docID = disi.nextDoc();
            if (docID == DocIdSetIterator.NO_MORE_DOCS) {
                throw new TransformerException("document '" +  href + "' not found");
            }
            XdmNode doc = getDocReader().get(docID, getSearcher().getIndexReader());
            return doc.asSource(); 
        } catch (IOException e) {
            throw new TransformerException(e);
        }
    }

    public XdmResultSet evaluate(String query) {
        XQueryExecutable compiledQuery = compiler.compile(query);
        queryStats.optimizedQuery = compiler.getLastOptimized().toString();
        return evaluate (compiledQuery, null);
    }
    
    public XdmResultSet evaluate(XQueryExecutable exec) {
        return evaluate (exec, null);
    }
    
    public XdmResultSet evaluate(XQueryExecutable expr, QueryContext context) {
        return iterate (expr, context);
    }

    public XdmResultSet iterate(XQueryExecutable xqueryExec, QueryContext context) { 
        if (context == null) {
            context = new QueryContext();
        }
        // FIXME: is this needed?
        Configuration config = compiler.getProcessor().getUnderlyingConfiguration();
        config.setCollectionURIResolver(this);
        try {
            XQueryEvaluator eval = xqueryExec.load();
            eval.setErrorListener(compiler.getErrorListener());
            if (context != null) {
                eval.setContextItem((XdmItem) context.getContextItem());
                if (context.getVariableBindings() != null) {
                    for (Map.Entry<QName, Object> binding : context.getVariableBindings().entrySet()) {
                        net.sf.saxon.s9api.QName saxonQName = new net.sf.saxon.s9api.QName(binding.getKey());
                        eval.setExternalVariable(saxonQName, (XdmValue) binding.getValue());
                    }
                }
            }
            XdmValue value = eval.evaluate();
            return new XdmResultSet (value);
        } catch (SaxonApiException e) {
            return new XdmResultSet(getCompiler().getErrorListener().getErrors());
        } finally {
            // TODO: share more globally and devise a time- or space-based expiration policy, 
            // plus flushing when the searcher is changed (since docIDs may change then)
            invalidateCache();
        }
    }

    public DocBuilder getBuilder() {
        return saxonBuilder;
    }
    
    public CachingDocReader getDocReader() {
        return docReader;
    }    
    
    /**
     * implements CollectionURIResolver so as to resolve uris in service of fn:collection() (and fn:uri-collection()).
     * For now, all collections retrieve all documents.  TODO: implement directory-based collections.
     */
    public SequenceIterator<?> resolve(String href, String base, XPathContext context) throws XPathException {
        if (href == null) {
            return new Search().iterate(new MatchAllDocsQuery(), this, 0, null);
        }
        if (href.startsWith("lux:")) {
            // Saxon doesn't actually enforce that this is a valid URI, and we don't care about that either
            String query = href.substring(4);
            Query q;
            try {
                // TODO: use a prebuilt parser, don't construct a new one for every query
                q = getLuxQueryParser().parse(query);
            } catch (ParseException e) {
                throw new XPathException ("Failed to parse query: " + query, e);
            }
            LoggerFactory.getLogger(getClass()).debug("executing query: {}", q);

            return new Search().iterate(q, this, 0, null);
        }
        return compiler.getDefaultCollectionURIResolver().resolve(href, base, context);
    }
    
    /**
     * reopen the searcher so it sees any updates
     */
    public void reopenSearcher() {
        try {
            searcher = new LuxSearcher (searcher.getIndexReader().reopen());
        } catch (IOException e) {
            throw new LuxException (e);
        }
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
    
    public XCompiler getCompiler() {
        return compiler;
    }

    public void invalidateCache() {
        if (docReader != null) {
            docReader.clear();
        }
    }

    public DocWriter getDocWriter() {
        return docWriter;
    }
    
    public LuxQueryParser getLuxQueryParser() {
        if (queryParser == null) {
            queryParser = LuxQueryParser.makeLuxQueryParser(compiler.getIndexConfiguration());
        }
        return queryParser;
    }

    public XmlQueryParser getXmlQueryParser () {
        if (xmlQueryParser == null) {
            IndexConfiguration config = compiler.getIndexConfiguration();
            FieldDefinition field = config.getField(FieldName.ELEMENT_TEXT);
            xmlQueryParser = new XmlQueryParser(config.getFieldName(field), field.getAnalyzer());
        }
        return xmlQueryParser;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
