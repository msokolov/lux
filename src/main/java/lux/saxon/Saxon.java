/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

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
import lux.xml.XmlBuilder;
import lux.xpath.AbstractExpression;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
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

    private XPathCompiler xpathCompiler;
    private SaxonBuilder saxonBuilder;
    private SaxonTranslator translator;
    
    // This is a volatile thing that needs to be reset for every new query
    // it should probably be associated with the Context? TODO: clean up the object model
    // here and make an object that has only things that have a query lifespan
    private CachingDocReader docReader;
    
    private static Config config;    
    
    public Saxon() {
        if (config == null) {
            config = new Config();
            config.setDocumentNumberAllocator(new DocIDNumberAllocator());
        }
        processor = new Processor (config);
        processor.registerExtensionFunction(new LuxSearch(this));
        processor.registerExtensionFunction(new LuxCount(this));
        processor.registerExtensionFunction(new LuxExists(this));
        //processor.registerExtensionFunction(new LuxRoot());

        xpathCompiler = processor.newXPathCompiler();
        xpathCompiler.declareNamespace("lux", "lux");
        saxonBuilder = new SaxonBuilder();
        translator = new SaxonTranslator(config);
    }

    @Override
    public SaxonExpr compile(String exprString) throws LuxException {
        XPathExecutable xpath;
        try {
           xpath = xpathCompiler.compile(exprString);
        } catch (SaxonApiException e) {
            throw new LuxException ("Syntax error compiling: " + exprString, e);
        }

        AbstractExpression expr = translator.exprFor(xpath.getUnderlyingExpression().getInternalExpression());
        PathOptimizer optimizer = new PathOptimizer(getContext().getIndexer());
        expr = optimizer.optimize(expr);
        try {
            xpath = xpathCompiler.compile(expr.toString());
        } catch (SaxonApiException e) {
            throw new LuxException ("Syntax error compiling: " + expr.toString(), e);
        }
        return new SaxonExpr(xpath, expr);
    }

    @Override
    public ResultSet<?> evaluate(Expression expr) {
        return evaluate (expr, getContext().getContextItem());       
    }
    
    @Override
    public ResultSet<?> evaluate(Expression expr, Object contextItem) {
        return iterate (expr, contextItem);
    }

    @Override
    public ResultSet<?> iterate(Expression expr, Object contextItem) { 
        docReader = new CachingDocReader(getContext().getSearcher().getIndexReader(), getBuilder(), getContext().getXmlFieldName());
        SaxonExpr saxonExpr = (SaxonExpr) expr;
        try {
            return saxonExpr.evaluate((XdmItem) contextItem);
        } catch (SaxonApiException e) {
            throw new LuxException (e);
        }
    }

    public SaxonBuilder getBuilder() {
        return saxonBuilder;
    }
    
    public SaxonContext getContext() {
        return (SaxonContext) super.getContext();
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
            config.getDocumentNumberAllocator().setDocID(docID);
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
