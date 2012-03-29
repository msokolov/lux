package lux.jaxen;

import java.io.Reader;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jaxen.JaxenException;
import org.jdom.Document;

import lux.api.Evaluator;
import lux.api.Expression;
import lux.api.LuxException;
import lux.xml.JDOMBuilder;
import lux.xml.XmlBuilder;

public class JaxenEvaluator extends Evaluator {    

    private XmlBuilder builder;
    
    public Expression compile(String exprString) throws LuxException {
        try {
            return new LuXPathBasic (exprString);
        } catch (JaxenException e) {
            throw new LuxException (e);
        }
    }

    public Object evaluate(Expression expr) {
        try {
            LuXPath luxpath =  ((LuXPath)expr);
            Object result = luxpath.evaluate(getContext());
            queryStats = luxpath.getQueryStats();
            return result;
        } catch (JaxenException e) {
            throw new LuxException (e);
        }
    }
    
    public JaxenContext getContext() {
        return (JaxenContext) super.getContext();
    }

    @Override
    public List<?> evaluate(Expression xpath, Object contextItem) {
        try {
            LuXPath luxpath =  ((LuXPath )xpath);
            List<?> result = luxpath.selectNodes(contextItem);
            queryStats = luxpath.getQueryStats();
            return result;
        } catch (JaxenException e) {
            throw new LuxException (e);
        }
    }

    @Override
    public Iterable<?> iterate(Expression xpath, Object contextItem) {        
        return evaluate(xpath, contextItem);
    }

    @Override
    public XmlBuilder getBuilder() {
        if (builder== null) {
            builder = new JaxenBuilder();
        }
        return builder;
    }
    
    class JaxenBuilder extends XmlBuilder {

        private JDOMBuilder jdomBuilder;
        JaxenBuilder() {
            jdomBuilder = new JDOMBuilder();
            addHandler(jdomBuilder);
        }

        @Override
        public Document build(Reader reader) {
           try {
            read (reader);
           } catch (XMLStreamException e) {
               throw new LuxException (e);
           }
           return jdomBuilder.getDocument();
        }
        
    }

}
