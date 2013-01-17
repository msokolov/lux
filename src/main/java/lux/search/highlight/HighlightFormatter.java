package lux.search.highlight;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Called by the XmlHighlighter to write highlighted terms.
 */
public interface HighlightFormatter {

    /**
     * Write StAX events that "highlight" the text
     * 
     * @param writer writes to the highlighted document
     * @param text a term or phrase that matched a query, to be highlighted
     * @throws XMLStreamException
     */
    public abstract void highlightTerm(XMLStreamWriter writer, String text) throws XMLStreamException;

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
