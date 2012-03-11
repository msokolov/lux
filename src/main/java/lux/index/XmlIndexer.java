package lux.index;

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import lux.xml.XmlReader;

public class XmlIndexer {
    
    private XmlReader xmlReader;
    // private JDOMBuilder jdomBuilder;
    private XmlPathMapper pathMapper;
    private List<String> fieldNames = new ArrayList<String>();
    
    private String eltNameFieldName = "lux_elt_name_ms";
    private String attNameFieldName = "lux_att_name_ms";
    private String pathFieldName = "lux_path_ms";
    
    public XmlIndexer () {
        xmlReader = new XmlReader();

        // build a JDOM in case we want to index XPaths
        // jdomBuilder = new JDOMBuilder();
        // accumulate XML paths and QNames for indexing
        pathMapper = new XmlPathMapper();
        // xmlReader.addHandler (jdomBuilder);
        xmlReader.addHandler (pathMapper);
        fieldNames.add(eltNameFieldName);
        fieldNames.add(attNameFieldName);
        fieldNames.add(pathFieldName);
    }
    
    public void index (InputStream xml) throws XMLStreamException {
        reset();
        xmlReader.read (xml);
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
    
    public Collection<?> getFieldValues (String fieldName) {
        if (eltNameFieldName.equals(fieldName)) {
            return pathMapper.getEltQNameCounts().keySet();
        }
        if (attNameFieldName.equals(fieldName)) {
            return pathMapper.getAttQNameCounts().keySet();
        }
        if (pathFieldName.equals(fieldName)) {
            return pathMapper.getPathCounts().keySet();
        }
        return Collections.EMPTY_SET;
    }
}
