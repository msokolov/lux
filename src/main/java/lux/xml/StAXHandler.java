/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xml;

import javax.xml.stream.XMLStreamReader;

public interface StAXHandler {

    /**
     * This method receives StAX events.  It should never call XMLStreamReader.next() 
     * since if it does, its caller will miss the event.  A cleaner API would be to
     * wrap the reader in a read-only wrapper class.
     */
    void handleEvent (XMLStreamReader reader, int eventType);


}/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
