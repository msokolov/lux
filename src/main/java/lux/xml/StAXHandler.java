package lux.xml;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * A receiver of XMLStreamReader (StAX) push-style events 
 */
public interface StAXHandler {

    /**
     * This method receives StAX events.  It should never call XMLStreamReader.next() 
     * since if it does, its caller will miss the event.  TODO: A cleaner API would be to
     * wrap the reader in a read-only wrapper class.
     * @param reader the reader from which events are being read
     * @param eventType the XML stream event type
     * @throws XMLStreamException 
     */
    void handleEvent (XMLStreamReader reader, int eventType) throws XMLStreamException;

    /**
     * reinitialize any internal state and prepare the handler for re-use
     */
    void reset();

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
