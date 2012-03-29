package lux.solr;

import java.io.StringReader;
import java.util.Collection;

import javax.xml.stream.XMLStreamException;

import org.apache.solr.common.util.NamedList;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import lux.api.Evaluator;
import lux.api.LuxException;
import lux.jaxen.JaxenEvaluator;
import lux.xml.JDOMBuilder;
import lux.xml.XmlReader;

public class JaxenComponent extends XPathSearchComponent {

    private XMLOutputter xmlOutputter = new XMLOutputter();
    private XmlReader xmlReader;
    private JDOMBuilder jdomBuilder;
    
    public JaxenComponent () {
        xmlOutputter.setFormat(Format.getCompactFormat().setOmitDeclaration(true)); 
        xmlReader = new XmlReader ();
        jdomBuilder = new JDOMBuilder ();
        xmlReader.addHandler(jdomBuilder);
    }
    
    @Override
    public Evaluator createEvaluator() {
        return new JaxenEvaluator();
    }

    @Override
    public Object buildDocument(String xml) {
        try {
            xmlReader.read(new StringReader (xml));
            return jdomBuilder.getDocument();
        } catch (XMLStreamException e) {
           throw new LuxException (e);
        }
    }
    
    public void addResult(NamedList<Object> xpathResults, Object result) {
        // TODO: review XPath 1.0 types and make sure we're covering them
        if (result instanceof Collection) {
            Collection<?> c = (Collection<?>) result;
            for (Object o :  c) {
                addResult (xpathResults, o);
            }
        }
        else if (result instanceof Element) {
            xpathResults.add("element", xmlOutputter.outputString((Element) result));
        } else if (result instanceof org.jdom.Attribute) {
            xpathResults.add ("attribute", ((org.jdom.Attribute)result).getValue());
        } else if (result instanceof org.jdom.Text) {
            xpathResults.add ("text", result.toString());
        } else if (result instanceof org.jdom.Document) {
            xpathResults.add ("document", xmlOutputter.outputString((org.jdom.Document) result));
        } else if (result instanceof Integer) {
            xpathResults.add ("xs:integer", result);
        } else if (result instanceof Double) {
            xpathResults.add ("xs:double", result);
        } else {
            xpathResults.add("xs:string", result.toString());
        }
    }

}
