package lux.search.highlight;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Utility class for highlighting that surrounds highlighted text with a single element
 */
public class TagFormatter implements HighlightFormatter {
	
	private final String localName;
	private final String namespaceURI;
	
	public TagFormatter (String localName, String namespaceURI) {
		this.localName = localName;
		this.namespaceURI = namespaceURI == null ? "" : namespaceURI;
	}

    /* (non-Javadoc)
     * @see lux.search.highlight.Highlighter#highlightTerm(javax.xml.stream.XMLStreamWriter, java.lang.String)
     */
    @Override
    public void highlightTerm(XMLStreamWriter writer, String text) throws XMLStreamException {
        writer.writeStartElement(namespaceURI, localName);
        writer.writeCharacters(text);
        writer.writeEndElement();
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
