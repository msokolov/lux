package lux.index;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.util.HashMap;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;

import lux.xml.StAXHandler;

/**
 * Accumulate counts of QNames and QName paths.
 * 
 * TODO: track char offsets and path id for each node
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
            currentPath.append ('/');
            currentPath.append(qname.toString());
            incrCount(eltQNameCounts, qname);
            String curPath = currentPath.toString();
            incrCount(pathCounts, curPath);
            for (int i = 0; i < reader.getAttributeCount(); i++) {
                QName attQName = getEventAttQName (reader, i);
                incrCount (attQNameCounts, attQName);
                incrCount(pathCounts, curPath + "/@" + attQName.toString());
            }
        }
        else if (eventType == END_ELEMENT) {
            QName qname = getEventQName(reader);
            // snip off the last path step, including its '/' separator char
            currentPath.setLength(currentPath.length() - qname.toString().length() - 1);
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
            return new QName (localName, namespace);
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

    public void clear() {
        eltQNameCounts.clear();
        attQNameCounts.clear();
        pathCounts.clear();
    }

}
