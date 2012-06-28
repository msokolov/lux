package lux.xml;

import java.io.InputStream;
import java.io.Reader;

import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;


/**
 * Reads XML and passes events to a brigade of StAXHandlers Essentially
 * turns StAX into push model parser a'la SAX.
 * 
 * @author sokolov
 *
 */
public class XmlReader {

    private XMLInputFactory inputFactory;

    private ArrayList<StAXHandler> handlers = new ArrayList<StAXHandler>();

    /**
     * Consume the character stream, generating events for the handlers.
     *
     * @param reader source of xml StAX events
     * @throws XMLStreamException if the reader does
     */
    public void read (Reader reader) throws XMLStreamException {        
        read (getXMLInputFactory().createXMLStreamReader(reader));
    }
    
    /**
     * Consume the character stream, generating events for the handlers.
     *
     * @param reader source of xml StAX events
     * @throws XMLStreamException if the reader does
     */
    public void read (InputStream in) throws XMLStreamException {        
        read (getXMLInputFactory().createXMLStreamReader(in));
    }
    
    private XMLInputFactory getXMLInputFactory () {
        if (inputFactory == null) {
            inputFactory = XMLInputFactory.newInstance();
            inputFactory.setProperty (XMLInputFactory.IS_COALESCING, false);
            inputFactory.setProperty (XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true);
            //inputFactory.setProperty (XMLInputFactory2.P_REPORT_PROLOG_WHITESPACE, false);            
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
        sendEvent (in, XMLStreamConstants.START_DOCUMENT);
        for (;;) {
            int event = in.next();
            sendEvent(in, event);
            if (event == XMLStreamConstants.END_DOCUMENT)
                break;
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
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
