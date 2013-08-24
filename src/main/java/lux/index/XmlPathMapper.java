package lux.index;

import static javax.xml.stream.XMLStreamConstants.*;

import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLStreamReader;

import lux.xml.StAXHandler;

import org.apache.commons.lang.StringUtils;

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
    
    protected MutableString currentPath = new MutableString(2048);
    private MutableString currentQName = new MutableString();
    private HashMap<CharSequence, Integer> eltQNameCounts = new HashMap<CharSequence, Integer>();
    private HashMap<CharSequence, Integer> attQNameCounts = new HashMap<CharSequence, Integer>();
    private HashMap<CharSequence, Integer> pathCounts = new HashMap<CharSequence, Integer>();
    private HashMap<CharSequence, CharSequence> names = new HashMap<CharSequence, CharSequence>();
    
    public Map<CharSequence,Integer> getEltQNameCounts () {
        return eltQNameCounts;
    }
    
    public Map<CharSequence,Integer> getAttQNameCounts () {
        return attQNameCounts;
    }
    
    public Map<CharSequence,Integer> getPathCounts () {
        return pathCounts;
    }
    
    public int getEltQNameCount (String s) {
        Integer i = eltQNameCounts.get(new MutableString (s));
        if (i == null) {
            return 0;
        }
        return i;
    }
    
    public int getAttQNameCount (String s) {
        Integer i = attQNameCounts.get(new MutableString (s));
        if (i == null) {
            return 0;
        }
        return i;
    }
    
    public int getPathCount (String s) {
        Integer i = pathCounts.get(new MutableString (s));
        if (i == null) {
            return 0;
        }
        return i;
    }
    
    public CharSequence getCurrentQName () {
        return names.get(currentQName);
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

    @Override
    public void handleEvent(XMLStreamReader reader, int eventType) {
        if (eventType == START_ELEMENT) {
            getEventQName(currentQName, reader);
            // qnameStack.add(qname);
            currentPath.append (' ');
            currentPath.append(currentQName);
            incrCount(eltQNameCounts, currentQName);
            incrCount(pathCounts, currentPath);
            int len = currentPath.length();
            for (int i = 0; i < reader.getAttributeCount(); i++) {
                getEventAttQName (currentQName, reader, i);
                incrCount (attQNameCounts, currentQName);
                currentPath.append(" @").append(currentQName);
                incrCount (pathCounts, currentPath);
                currentPath.setLength(len);
            }
        }
        else if (eventType == END_ELEMENT) {
            getEventQName(currentQName, reader);
            // snip off the last path step, including its '/' separator char
            currentPath.setLength(currentPath.length() - currentQName.length() - 1);
        }
        else if (eventType == START_DOCUMENT) {
            currentPath.append("{}");
        }
    }

    protected void getEventAttQName(MutableString buf, XMLStreamReader reader, int i) {
        encodeQName (buf, reader.getAttributeLocalName(i), reader.getAttributePrefix(i), reader.getAttributeNamespace(i));
    }

    private void getEventQName(MutableString buf, XMLStreamReader reader) {
        encodeQName (buf, reader.getLocalName(), reader.getPrefix(), reader.getNamespaceURI());
    }
    
    private void encodeQName (MutableString buf, String localName, String prefix, String namespace) {
        buf.setLength(0);
        if (namespaceAware) {
            buf.append(localName);
            if (!StringUtils.isEmpty(namespace)) {
                buf.append ('{').append(namespace).append("}");
            }
        } 
        else if (! prefix.isEmpty()) {
            buf.append(prefix).append(':').append(localName);
        }
        else {
            buf.append (localName);
        } 
    }

    private void incrCount(HashMap<CharSequence, Integer> map, MutableString o) {
        if (map.containsKey(o))
            map.put(o, map.get(o) + 1);
        else {
            MutableString copy = new MutableString(o);
            map.put(copy, 1);
            names.put(copy, copy);
        }
    }
    
    @Override
    public void reset() {
        eltQNameCounts.clear();
        attQNameCounts.clear();
        pathCounts.clear();
        names.clear();
        currentPath.setLength(0);
        currentQName.setLength(0);
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
