package lux.xml;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.evpull.BracketedDocumentIterator;
import net.sf.saxon.evpull.Decomposer;
import net.sf.saxon.evpull.EventIterator;
import net.sf.saxon.evpull.EventToStaxBridge;
import net.sf.saxon.evpull.SingletonEventIterator;
import net.sf.saxon.om.DocumentInfo;
import net.sf.saxon.om.NodeInfo;

import org.codehaus.stax2.XMLInputFactory2;

import com.ctc.wstx.api.WstxInputProperties;

/**
 * Reads XML and passes events to a brigade of StAXHandlers. Essentially
 * turns StAX into push model parser a'la SAX.
 * 
 * @author sokolov
 *
 */
public class XmlReader {

    private XMLInputFactory inputFactory;

    private ArrayList<StAXHandler> handlers = new ArrayList<StAXHandler>();
    
    private boolean stripNamespaces;

    /**
     * Consume the character stream, generating events for the handlers.
     *
     * @param reader source of xml StAX events
     * @throws XMLStreamException if the reader does
     */
    public void read (Reader reader) throws XMLStreamException {
        XMLStreamReader xmlStreamReader = getXMLInputFactory().createXMLStreamReader(reader);
        if (stripNamespaces) {
            xmlStreamReader = new NamespaceStrippingXMLStreamReader(xmlStreamReader);
        }
        read (xmlStreamReader);
    }
    
    /**
     * Consume the byte stream, generating events for the handlers.
     *
     * @param in source of xml StAX events
     * @throws XMLStreamException if the reader does
     */
    public void read (InputStream in) throws XMLStreamException {  
        XMLStreamReader xmlStreamReader = getXMLInputFactory().createXMLStreamReader(in);
        if (stripNamespaces) {
            xmlStreamReader = new NamespaceStrippingXMLStreamReader(xmlStreamReader);
        }
        read (xmlStreamReader);
    }
    
    public void read (NodeInfo node) throws XMLStreamException {
        PipelineConfiguration pipe = node.getConfiguration().makePipelineConfiguration();
        pipe.setHostLanguage(Configuration.XQUERY);
        XMLStreamReader xmlStreamReader; 
        // copied from net.sf.saxon.xqj.SaxonXQItem
        if (node instanceof DocumentInfo) {
            EventIterator eventIterator = new Decomposer(node, pipe);
            xmlStreamReader = new EventToStaxBridge(eventIterator, pipe);
        } else {
            EventIterator contentIterator = new SingletonEventIterator(node);
            EventIterator eventIterator = new BracketedDocumentIterator(contentIterator);
            eventIterator = new Decomposer(eventIterator, pipe);
            xmlStreamReader = new EventToStaxBridge(eventIterator, pipe);
        }
        if (stripNamespaces) {
            xmlStreamReader = new NamespaceStrippingXMLStreamReader(xmlStreamReader);
        }
        read (xmlStreamReader);
    }
    
    private XMLInputFactory getXMLInputFactory () {
        if (inputFactory == null) {
            // We require Woodstox for its superior character-offset reporting, which
            // is broken and incomplete in the default (sun) StAX parser in the Oracle JVM.
            inputFactory = XMLInputFactory2.newInstance();
            inputFactory.setProperty (XMLInputFactory.IS_COALESCING, false);
            inputFactory.setProperty (XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true);
            inputFactory.setProperty (XMLInputFactory2.P_REPORT_PROLOG_WHITESPACE, false);
            inputFactory.setProperty 
                (XMLInputFactory2.RESOLVER, 
                 new XMLResolver() {
                     @Override
                    public Object resolveEntity
                         (String publicID,
                          String systemID,
                          String baseURI,
                          String namespace) throws XMLStreamException {
                         return new ByteArrayInputStream (new byte[0]);
                     }
                 });
            // this doesn't seem to do anything?
            // inputFactory.setProperty (WstxInputProperties.P_NORMALIZE_LFS, false);
            inputFactory.setProperty (WstxInputProperties.P_TREAT_CHAR_REFS_AS_ENTS, true);
            // must set this to 1 in order to get TREAT_CHAR_REFS_AS_ENTS to report entities?
            inputFactory.setProperty (WstxInputProperties.P_MIN_TEXT_SEGMENT, Integer.valueOf(1));
        }
        return inputFactory;
    }

    public void addHandler (StAXHandler handler) {
        handlers.add (handler);
    }
    
    public List<StAXHandler> getHandlers () {
        return handlers;
    }

    /**
     * Consume the XML stream, generating events for the handlers.
     *
     * @param in source of xml StAX events
     * @throws XMLStreamException if the reader does
     */
    public void read (XMLStreamReader in)
        throws XMLStreamException
    {
        boolean gotEndDocument = false;
        // wrap every pass in start/end document??
        sendEvent (in, XMLStreamConstants.START_DOCUMENT);
        while (in.hasNext()) {
            int event = in.next();
            if (event == XMLStreamConstants.START_DOCUMENT) {
                continue;
            }
            sendEvent(in, event);
            if (event == XMLStreamConstants.END_DOCUMENT) {
                gotEndDocument = true;
            }
        }
        if (! gotEndDocument) {
            sendEvent (in, XMLStreamConstants.END_DOCUMENT);
        }
    }

    private void sendEvent(XMLStreamReader in, int event) throws XMLStreamException {
        for (StAXHandler handler : handlers) {
            handler.handleEvent (in, event);
        }
    }
    
    public void reset () {
        for (StAXHandler handler : handlers) {
            handler.reset ();
        }
    }
    
    /**
     * when true, all namespace information is stripped from the reported events.
     * The result is as if all namespace declarations and prefixes were removed from the document.
     * @return whether namespace information is stripped
     */
    public boolean isStripNamespaces() {
        return stripNamespaces;
    }

    public void setStripNamespaces(boolean stripNamespaces) {
        this.stripNamespaces = stripNamespaces;
    }

    class NamespaceStrippingXMLStreamReader extends StreamReaderDelegate implements NamespaceContext {
        public NamespaceStrippingXMLStreamReader(XMLStreamReader xmlStreamReader) {
            super (xmlStreamReader);
        }

        @Override
        public String getPrefix () {
            return "";
        }
        
        @Override
        public String getNamespaceURI() {
            return "";
        }
        
        @Override
        public int getNamespaceCount() {
            return 0;
        }
        
        @Override
        public String getNamespaceURI(int i) {
            return "";
        }
        
        @Override
        public String getNamespaceURI(String s) {
            return "";
        }
        
        @Override
        public String getAttributePrefix (int i) {
            return "";
        }
        
        @Override
        public String getAttributeNamespace (int i) {
            return "";
        }
        
        @Override
        public NamespaceContext getNamespaceContext () {
            return this;
            // return super.getNamespaceContext();
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return "";
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            return new Iterator<String>() {
                @Override
                public boolean hasNext() {
                    return false;
                }

                @Override
                public String next() {
                    return null;
                }

                @Override
                public void remove() {
                }
            };
        }
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
