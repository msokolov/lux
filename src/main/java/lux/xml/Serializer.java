package lux.xml;

import java.util.HashMap;
import java.util.LinkedList;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import lux.exception.LuxException;

import org.codehaus.stax2.DTDInfo;
import org.codehaus.stax2.XMLStreamReader2;

/**
 * Accumulates a serialized XML document from StAX events according to a prescribed set of conventions,
 * as follows:
 * Line endings are normalized; CDATA blocks are preserved; entities and numeric references are preserved; redundant namespace 
 * declarations are dropped.  Comments and processing instructions are preserved.
 * No XML declaration is emitted. DOCTYPE declarations are preserved, but not the DTD internal subset.
 * Empty elements are represented with both open and close tags.
 */
public class Serializer implements StAXHandler {

    private StringBuilder doc;

    private HashMap<String,String> nsContextFrame;
    private HashMap<String,String> inScopeNamespaces;
    private LinkedList<HashMap<String,String>> namespaceContexts;
    
    public String getDocument () {
        return doc.toString();
    }

    /**
     * Consume the XML stream, saving text to the document buffer.
     *
     * @param r source of xml StAX events
     * @param evtType the type of StAX event
     */
    public void handleEvent (XMLStreamReader r, int evtType)
    {
        switch (evtType) {
        case XMLStreamConstants.START_DOCUMENT:
            inScopeNamespaces = new HashMap<String, String>();
            inScopeNamespaces.put("", "");
            inScopeNamespaces.put("xml", XMLConstants.W3C_XML_SCHEMA_NS_URI);
            namespaceContexts = new  LinkedList<HashMap<String,String>>();
            doc = new StringBuilder ();
            break;

        case XMLStreamConstants.CDATA:
            doc.append("<![CDATA[");
            doc.append(r.getTextCharacters(), r.getTextStart(), r.getTextLength());
            doc.append("]]>");
            break;

        case XMLStreamConstants.SPACE:
            // fall through

        case XMLStreamConstants.CHARACTERS:
            appendText(r);
            break;

        case XMLStreamConstants.COMMENT:
            doc.append("<!--");
            appendText(r);
            doc.append("-->");
            break;

        case XMLStreamConstants.END_DOCUMENT:
            doc.append('\n');
            break;

        case XMLStreamConstants.END_ELEMENT:
            doc.append("</");
            appendQName(r);
            doc.append(">");
            unwindNamespaceContext();
            break;

        case XMLStreamConstants.ENTITY_DECLARATION:
        case XMLStreamConstants.NOTATION_DECLARATION:
            break;

        case XMLStreamConstants.ENTITY_REFERENCE:
            doc.append('&').append(r.getLocalName()).append(';');
            break;

        case XMLStreamConstants.PROCESSING_INSTRUCTION:
            doc.append("<?").append(r.getPITarget()).append(' ').append(r.getPIData()).append("?>");
            break;

        case XMLStreamConstants.START_ELEMENT:
            {
                nsContextFrame = null;
                doc.append('<');
                appendQName(r);
                handleNamespace(r.getPrefix(), r.getNamespaceURI());
                // Any declared namespaces?
                for (int i = 0, len = r.getNamespaceCount(); i < len; ++i) {
                    handleNamespace (r.getNamespacePrefix(i), r.getNamespaceURI(i));
                }
                // And then the attributes:
                for (int i = 0, len = r.getAttributeCount(); i < len; ++i) {
                    String prefix = r.getAttributePrefix(i);
                    doc.append (' ');
                    if (prefix != null && prefix.length() > 0) {
                        doc.append (prefix).append(':');
                    }
                    String value = r.getAttributeValue(i).replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
                    doc.append(r.getAttributeLocalName(i)).append ("=\"").append(value).append('"');
                }
                doc.append(">");
                namespaceContexts.push(nsContextFrame);                
            }
            break;

        case XMLStreamConstants.DTD:
            DTDInfo dtd;
            try {
                dtd = ((XMLStreamReader2)r).getDTDInfo();
            } catch (XMLStreamException e) {
                break;
            }
            doc.append("<!DOCTYPE ");
            doc.append (dtd.getDTDRootName());
            if (dtd.getDTDPublicId() != null) {
                doc.append(" PUBLIC \"");
                doc.append (dtd.getDTDPublicId());
                doc.append('"');
            }
            if (dtd.getDTDSystemId() != null) {
                if (dtd.getDTDPublicId() == null) {
                    doc.append(" SYSTEM");
                }
                doc.append(" \"");
                doc.append (dtd.getDTDSystemId());
                doc.append('"');
            }
            doc.append (">\n");
            // Lose the DTD internal subset, sorry
            break;

        default:
            throw new LuxException("Unexpected StAX event: " + r.getEventType());
        }
    }

    private void appendText(XMLStreamReader r) {
        int end = r.getTextStart() + r.getTextLength();
        char [] c = r.getTextCharacters();
        for (int i = r.getTextStart(); i < end; i++) {
            // TODO: look ahead and copy characters in blocks?
            switch (c[i]) {
            case '<': doc.append("&lt;"); break;
            case '&': doc.append("&amp;"); break;
            default: doc.append(c[i]);
            }
        }
    }

    private void unwindNamespaceContext () {
        HashMap<String,String> nsContext = namespaceContexts.pop();
        if (nsContext != null) {
            inScopeNamespaces.putAll(nsContext);
        }
    }
    
    private String handleNamespace(String prefix, String namespace) {
        if (namespace == null) {
            namespace = "";
        }
        if (prefix == null) {
            prefix = "";
        }
        if (! namespace.equals(inScopeNamespaces.get(prefix))) {
            appendNamespaceDeclaration (prefix, namespace);
            if (nsContextFrame == null) {
                nsContextFrame = new HashMap<String, String>();
            }
            // retain the outer context so we can restore it later
            nsContextFrame.put(prefix, inScopeNamespaces.get(prefix));
            inScopeNamespaces.put (prefix, namespace);
        }
        return namespace;
    }

    private void appendNamespaceDeclaration(String prefix, String namespace) {
        doc.append (" xmlns");
        if (prefix.length() > 0) {
            doc.append (':').append(prefix);
        }
        doc.append("=\"");
        namespace = namespace.replace("\"", "&quot;");
        doc.append(namespace);
        doc.append("\"");
    }
    
    public void reset () {
        doc = null;
        namespaceContexts = null;
        inScopeNamespaces = null;
        namespaceContexts = null;
    }

    private void appendQName(XMLStreamReader r) {
        String elemPrefix = r.getPrefix();
        String ln = r.getLocalName();
        if (elemPrefix != null && elemPrefix.length() > 0) {
            doc.append (elemPrefix).append(':');
            
        } 
        doc.append(ln);
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
