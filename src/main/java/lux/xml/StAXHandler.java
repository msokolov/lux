package lux.xml;

import javax.xml.stream.XMLStreamReader;

public interface StAXHandler {

    /**
     * This method receives StAX events.  It should never call XMLStreamReader.next() 
     * since if it does, its caller will miss the event.  A cleaner API would be to
     * wrap the reader in a read-only wrapper class.
     */
    void handleEvent (XMLStreamReader reader, int eventType);

}