package lux.index;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jdom.Document;
import org.jdom.filter.ContentFilter;
import org.jdom.output.XMLOutputter;

import lux.xml.JDOMBuilder;
import lux.xml.XmlReader;

public class XmlIndexer {
    
    private XmlReader xmlReader;
    private JDOMBuilder jdomBuilder;
    private XMLOutputter jdomSerializer;
    private XmlPathMapper pathMapper;
    private List<String> fieldNames = new ArrayList<String>();
    
    private String eltNameFieldName = "lux_elt_name_ms";
    private String attNameFieldName = "lux_att_name_ms";
    private String pathFieldName = "lux_path_ms";
    private String xmlFieldName = "xml_text"; // TODO rename
    private String textFieldName = "xml_text_only";
    
    private int options = 0;
    
    public XmlIndexer () {
        xmlReader = new XmlReader();        
        // accumulate XML paths and QNames for indexing
        pathMapper = new XmlPathMapper();
        xmlReader.addHandler (pathMapper);
        fieldNames.add(eltNameFieldName);
        fieldNames.add(attNameFieldName);
        fieldNames.add(pathFieldName);
        fieldNames.add(textFieldName);
    }
    
    public static int BUILD_JDOM=1;
    public static int SERIALIZE_XML=2;
    
    public XmlIndexer (int options) {
        this ();
        this.options = options;
        if (isOption (BUILD_JDOM) || isOption(SERIALIZE_XML)) {
         // build a JDOM in case we want to index XPaths
            jdomBuilder = new JDOMBuilder();
            xmlReader.addHandler (jdomBuilder);
            if (isOption (SERIALIZE_XML)) {
                jdomSerializer = new XMLOutputter ();
                fieldNames.add(xmlFieldName);
            }
        }
    }
    
    public void index (InputStream xml) throws XMLStreamException {
        reset();
        xmlReader.read (xml);
    }

    private boolean isOption (int option) {
        return (options & option) != 0;
    }
    
    public void index (Reader xml) throws XMLStreamException {
        reset();
        xmlReader.read (xml);
    }
    
    private void reset() {
        pathMapper.clear();
    }
    
    public Collection<String> getFieldNames () {
        return fieldNames;
    }
    
    public Iterable<?> getFieldValues (String fieldName) {
        if (eltNameFieldName.equals(fieldName)) {
            return pathMapper.getEltQNameCounts().keySet();
        }
        if (attNameFieldName.equals(fieldName)) {
            return pathMapper.getAttQNameCounts().keySet();
        }
        if (pathFieldName.equals(fieldName)) {
            return pathMapper.getPathCounts().keySet();
        }
        if (xmlFieldName.equals(fieldName) && isOption(SERIALIZE_XML)) {
            return Collections.singletonList(jdomSerializer.outputString(getJDOM()));
        }
        if (textFieldName.equals(fieldName) && isOption(BUILD_JDOM)) {
           @SuppressWarnings("unchecked")
           final Iterator<Object> textIter = getJDOM().getDescendants (new ContentFilter(ContentFilter.TEXT | ContentFilter.CDATA));
           return new Iterable<Object> () {
            public Iterator<Object> iterator() {
                return textIter;
            }
           };
        }
        return Collections.EMPTY_SET;
    }
    
    public boolean isTokens (String fieldname) {
        return textFieldName.equals(fieldname);
    }
    
    public String getTextFieldName () {
        return textFieldName;
    }
    
    public Document getJDOM() {
        if (jdomBuilder == null)
            return null;
        return jdomBuilder.getDocument();
    }
}
