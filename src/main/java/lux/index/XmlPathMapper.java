package lux.index;

import static javax.xml.stream.XMLStreamConstants.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;

import lux.xml.StAXHandler;

/**
 * Accumulate counts of QNames and QName paths.
 * 
 * @author sokolov
 *
 */
public class XmlPathMapper implements StAXHandler {
    
    private StringBuilder currentPath = new StringBuilder();
    private HashMap<QName, Integer> eltQNameCounts = new HashMap<QName, Integer>();
    private HashMap<QName, Integer> attQNameCounts = new HashMap<QName, Integer>();
    private HashMap<String, Integer> pathCounts = new HashMap<String, Integer>();
    
    public Map<QName,Integer> getEltQNameCounts () {
        return eltQNameCounts;
    }
    
    public Map<QName,Integer> getAttQNameCounts () {
        return attQNameCounts;
    }
    
    public Map<String,Integer> getPathCounts () {
        return pathCounts;
    }
    
    private boolean namespaceAware = false;

    /*
     * If false, the default, QNames are represented using prefix:localName without regard to
     * any prefix to namespace mapping.  Otherwise, XML namespaces are handled in the usual way.
     */
    public boolean isNamespaceAware() {
        return namespaceAware;
    }

    public void setNamespaceAware(boolean namespaceAware) {
        this.namespaceAware = namespaceAware;
    }

    public void handleEvent(XMLStreamReader reader, int eventType) {
        if (eventType == START_ELEMENT) {
            QName qname = getEventQName(reader);
            // qnameStack.add(qname);
            currentPath.append (' ');
            currentPath.append(encodeQName(qname));
            incrCount(eltQNameCounts, qname);
            String curPath = currentPath.toString();
            incrCount(pathCounts, curPath);
            for (int i = 0; i < reader.getAttributeCount(); i++) {
                QName attQName = getEventAttQName (reader, i);
                incrCount (attQNameCounts, attQName);
                incrCount(pathCounts, curPath + " @" + encodeQName(attQName));
            }
        }
        else if (eventType == END_ELEMENT) {
            QName qname = getEventQName(reader);
            // snip off the last path step, including its '/' separator char
            currentPath.setLength(currentPath.length() - encodeQName(qname).length() - 1);
        }
        else if (eventType == START_DOCUMENT) {
            currentPath.append("{}");
        }
    }

    private QName getEventAttQName(XMLStreamReader reader, int i) {
        return createQName (reader.getAttributeLocalName(i), reader.getAttributePrefix(i), reader.getAttributeNamespace(i));
    }

    private QName getEventQName(XMLStreamReader reader) {
        return createQName (reader.getLocalName(), reader.getPrefix(), reader.getNamespaceURI());
    }
    
    private QName createQName (String localName, String prefix, String namespace) {
        if (namespaceAware) {
            return new QName (namespace, localName);
        } 
        else if (! prefix.isEmpty()) {
            return new QName (prefix +':' + localName);
        }
        else {
            return new QName (localName);
        } 
    }

    private <T> void incrCount(HashMap<T, Integer> map, T o) {
        if (map.containsKey(o))
            map.put(o, map.get(o) + 1);
        else
            map.put(o, 1);
    }
    
    /**
     * encode a QName in a suitable form for indexing.  Escapes namespace uris URL-escaping
     * so that no spaces occur, and space can be used as a separator.  
     * If namespace-aware, the encoding is: local-name{encoded-namespace}.  Otherwise,
     * if prefix is non-empty, it's local-name{prefix}, otherwise just local-name.
     * @param qname
     * @return the encoded qname
     */
    private String encodeQName (QName qname) {
        String encns = null;
        if (!isNamespaceAware()) {
            if (qname.getPrefix().isEmpty()) {
                return qname.getLocalPart();
            }
            encns = qname.getPrefix();
        } else {
            if (qname.getNamespaceURI().isEmpty()) {
                return qname.getLocalPart();
            }
            try {
                encns = URLEncoder.encode(qname.getNamespaceURI(), "utf-8");
            } catch (UnsupportedEncodingException e) { }
        }
        return qname.getLocalPart() + '{' + encns + '}';
    }
    
    public void clear() {
        eltQNameCounts.clear();
        attQNameCounts.clear();
        pathCounts.clear();
        currentPath.setLength(0);
    }

}
