package lux.saxon;

import java.io.Reader;

import javax.xml.transform.stream.StreamSource;

import lux.PathOptimizer;
import lux.api.Evaluator;
import lux.api.Expression;
import lux.api.LuxException;
import lux.api.ResultSet;
import lux.functions.LuxCount;
import lux.functions.LuxExists;
import lux.functions.LuxSearch;
import lux.index.XmlIndexer;
import lux.lucene.LuxSearcher;
import lux.xml.XmlBuilder;
import lux.xquery.XQuery;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;

/**
 * Implementation of a lux query/expression evaluator using Saxon
 *
 */
public class Saxon extends Evaluator  {

    private Processor processor;
    public Processor getProcessor() {
        return processor;
    }

    private XQueryCompiler xqueryCompiler;
    private XPathCompiler xpathCompiler;
    private SaxonBuilder saxonBuilder;
    private SaxonTranslator translator;
    
    private CachingDocReader docReader;
    
    private Config config;    
    
    public Saxon(LuxSearcher searcher, XmlIndexer indexer) {
        super (searcher, indexer);
        config = new Config(this);
        config.setDocumentNumberAllocator(new DocIDNumberAllocator());
        config.setConfigurationProperty(FeatureKeys.XQUERY_PRESERVE_NAMESPACES, false);
        processor = new Processor (config);
        processor.registerExtensionFunction(new LuxSearch());
        processor.registerExtensionFunction(new LuxCount());
        processor.registerExtensionFunction(new LuxExists());
        //processor.registerExtensionFunction(new LuxRoot());

        xpathCompiler = processor.newXPathCompiler();
        xpathCompiler.declareNamespace("lux", "lux");
        xqueryCompiler = processor.newXQueryCompiler();
        xqueryCompiler.declareNamespace("lux", "lux");
        
        saxonBuilder = new SaxonBuilder();
        translator = new SaxonTranslator(config);
    }

    @Override
    public SaxonExpr compile(String exprString) throws LuxException {
        //XPathExecutable xpath;
        // xpathCompiler.setErrorListener (config.getErrorListener());
        xqueryCompiler.setErrorListener(config.getErrorListener());
        XQueryExecutable xquery;
        try {
            xquery = xqueryCompiler.compile(exprString);
            // xpath = xpathCompiler.compile(exprString);
        } catch (SaxonApiException e) {
            throw new LuxException ("Syntax error compiling: " + exprString, e);
        }
        XQuery abstractQuery = translator.queryFor (xquery);
        //AbstractExpression expr = translator.exprFor(xpath.getUnderlyingExpression().getInternalExpression());
        //AbstractExpression expr = translator.exprFor(xquery.getUnderlyingCompiledQuery().getExpression());
        PathOptimizer optimizer = new PathOptimizer(getIndexer());
        XQuery optimizedQuery = optimizer.optimize(abstractQuery);
        try {
            xquery = xqueryCompiler.compile(optimizedQuery.toString());
            // xpath = xpathCompiler.compile(expr.toString());
        } catch (SaxonApiException e) {
            throw new LuxException ("Syntax error compiling: " + optimizedQuery.toString(), e);
        }
        //return new SaxonExpr(xpath, expr);
        return new SaxonExpr(xquery, optimizedQuery);
    }

    @Override
    public ResultSet<?> evaluate(Expression expr) {
        return evaluate (expr, null);
    }
    
    @Override
    public ResultSet<?> evaluate(Expression expr, Object contextItem) {
        return iterate (expr, contextItem);
    }

    @Override
    public ResultSet<?> iterate(Expression expr, Object contextItem) { 
        docReader = new CachingDocReader(getSearcher().getIndexReader(), getBuilder(), getIndexer().getXmlFieldName());
        SaxonExpr saxonExpr = (SaxonExpr) expr;
        try {
            return saxonExpr.evaluate((XdmItem) contextItem);
        } catch (SaxonApiException e) {
            throw new LuxException (e);
        } finally {
            docReader.clear();
            docReader = null;
        }
    }

    public SaxonBuilder getBuilder() {
        return saxonBuilder;
    }
    
    public Config getConfig() {
        return config;
    }
    
    public class SaxonBuilder extends XmlBuilder {
        private DocumentBuilder documentBuilder;

        SaxonBuilder () {
            documentBuilder = processor.newDocumentBuilder();
        }
        
        public XdmNode build (Reader reader, int docID) {
            config.getDocumentNumberAllocator().setNextDocID(docID);
            return (XdmNode) build(reader);
        }

        @Override
        public Object build(Reader reader) {
            try {
                return documentBuilder.build(new StreamSource (reader));
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
        xpathCompiler.declareNamespace(prefix, namespace);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
