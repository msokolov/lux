package lux.search.highlight;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public class HtmlBoldFormatter implements HighlightFormatter {

    /* (non-Javadoc)
     * @see lux.search.highlight.Highlighter#highlightTerm(javax.xml.stream.XMLStreamWriter, java.lang.String)
     */
    @Override
    public void highlightTerm(XMLStreamWriter writer, String text) throws XMLStreamException {
        writer.writeStartElement("B");
        writer.writeCharacters(text);
        writer.writeEndElement();
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
