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
 * Each path is a string of path components, separated by single space characters.
 * The first path component is always <code>{}</code>.  The others are element 
 * QNames of the form <code>local-name{namespace}</code>, where <code>{namespace}</code> is omitted when the namespace 
 * is empty. The sequence of element QNames may be followed by a single attribute QName of the form:
 * <code>@local-name{namespace}</code>.  Namespaces are encoded using URL-encoding so they will not 
 * contain unexpected characters (such as space and {}).
 * 
 * TODO: a bunch of optimizations are possible here; there is a duplication of work in the subclasses,
 * unneeded String creation, etc.  Come back and fix that once we've settled on a definite implementation!
 */
public class XmlPathMapper implements StAXHandler {
    
    protected StringBuilder currentPath = new StringBuilder();
    protected QName currentQName;
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
    
    private boolean namespaceAware = true;

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
            currentQName = getEventQName(reader);
            // qnameStack.add(qname);
            currentPath.append (' ');
            currentPath.append(encodeQName(currentQName));
            incrCount(eltQNameCounts, currentQName);
            String curPath = currentPath.toString();
            incrCount(pathCounts, curPath);
            for (int i = 0; i < reader.getAttributeCount(); i++) {
                QName attQName = getEventAttQName (reader, i);
                incrCount (attQNameCounts, attQName);
                incrCount (pathCounts, curPath + " @" + encodeQName(attQName));
            }
        }
        else if (eventType == END_ELEMENT) {
            currentQName = getEventQName(reader);
            // snip off the last path step, including its '/' separator char
            currentPath.setLength(currentPath.length() - encodeQName(currentQName).length() - 1);
        }
        else if (eventType == START_DOCUMENT) {
            currentPath.append("{}");
        }
    }

    protected QName getEventAttQName(XMLStreamReader reader, int i) {
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
    protected String encodeQName (QName qname) {
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
    
    public void reset() {
        eltQNameCounts.clear();
        attQNameCounts.clear();
        pathCounts.clear();
        currentPath.setLength(0);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
