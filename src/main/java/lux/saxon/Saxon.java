package lux.saxon;

import java.io.Reader;

import javax.xml.transform.stream.StreamSource;

import lux.XPathCollector;
import lux.XPathQuery;
import lux.api.Evaluator;
import lux.api.Expression;
import lux.api.LuxException;
import lux.api.ResultSet;
import lux.xml.XmlBuilder;
import lux.xpath.AbstractExpression;
import lux.xpath.PathOptimizer;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XdmItem;

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
    private static Config config;
    
    public Saxon() {
        if (config == null) {
            config = new Config();
        }
        processor = new Processor (config);
        processor.registerExtensionFunction(new LuxSearch(this));
        processor.registerExtensionFunction(new LuxCount(this));
        processor.registerExtensionFunction(new LuxExists(this));
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
        PathOptimizer optimizer = new PathOptimizer();
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
    
    public XPathCollector getCollector (XPathQuery query) {
        return new XPathCollector (query, getBuilder(), queryStats);
    }

}
