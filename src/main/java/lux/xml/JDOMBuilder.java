/* This source code is derived from Tatu Saloranta and Bradley Huffman's
 * StAXBuilder, which in turn was based on code from the JDOM project by
 * Jason Hunter and Brett McLaughlin. The original copyright notice is
 * maintained.
 */

/*--

 Copyright (C) 2000-2004 Jason Hunter & Brett McLaughlin.
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions, and the disclaimer that follows
    these conditions in the documentation and/or other materials
    provided with the distribution.

 3. The name "JDOM" must not be used to endorse or promote products
    derived from this software without prior written permission.  For
    written permission, please contact <request_AT_jdom_DOT_org>.

 4. Products derived from this software may not be called "JDOM", nor
    may "JDOM" appear in their name, without prior written permission
    from the JDOM Project Management <request_AT_jdom_DOT_org>.

 In addition, we request (but do not require) that you include in the
 end-user documentation provided with the redistribution and/or in the
 software itself an acknowledgement equivalent to the following:
     "This product includes software developed by the
      JDOM Project (http://www.jdom.org/)."
 Alternatively, the acknowledgment may be graphical using the logos
 available at http://www.jdom.org/images/logos.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED.  IN NO EVENT SHALL THE JDOM AUTHORS OR THE PROJECT
 CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 SUCH DAMAGE.

 This software consists of voluntary contributions made by many
 individuals on behalf of the JDOM Project and was originally
 created by Jason Hunter <jhunter_AT_jdom_DOT_org> and
 Brett McLaughlin <brett_AT_jdom_DOT_org>.  For more information
 on the JDOM Project, please see <http://www.jdom.org/>.

 */

/*
 * Builds a JDOM {@link org.jdom.Document org.jdom.Document} using a
 * {@link javax.xml.stream.XMLStreamReader}.
 *
 * @version $Revision: 1.04 $, $Date: 2004/12/11 00:00:00 $
 * @author  Tatu Saloranta
 * @author  Bradley S. Huffman
 */

package lux.xml;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jdom.Attribute;
import org.jdom.CDATA;
import org.jdom.Comment;
import org.jdom.Content;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.EntityRef;
import org.jdom.Namespace;
import org.jdom.ProcessingInstruction;
import org.jdom.Text;

/**
 * Reads XML and generates a JDOM tree,

 * This is derived from Tatu Saloranta's StAXBuilder.  It has
 * been stripped down considerably by removing the JDOMFactory, removing
 * options related to whitespace stripping and DTD events, and retooled to 
 * behave as a StAXHandler so it can participate in an event brigade.
 * 
 * @author sokolov
 *
 */
public class JDOMBuilder implements StAXHandler {

    private Document doc;
    private Element current = null; // At top level

    public Document getDocument () {
        return doc;
    }

    /**
     * Consume the XML stream, producing a JDOM.
     *
     * @param in source of xml StAX events
     * @throws XMLStreamException if the reader does
     */
    public void handleEvent (XMLStreamReader r, int evtType)
    {
        Content child = null;

        switch (evtType) {
        case XMLStreamConstants.START_DOCUMENT:
            current = null;
            doc = new Document();
            break;

        case XMLStreamConstants.CDATA:
            child = new CDATA(r.getText());
            break;

        case XMLStreamConstants.SPACE:
            // fall through

        case XMLStreamConstants.CHARACTERS:
            /* Small complication: although (ignorable) white space
             * is allowed in prolog/epilog, and StAX may report such
             * event, JDOM barfs if trying to add it. Thus, let's just
             * ignore all textual stuff outside the tree:
             */
            if (current == null) {
                break;
            }
            child = new Text(r.getText());
            break;

        case XMLStreamConstants.COMMENT:
            child = new Comment(r.getText());
            break;

        case XMLStreamConstants.END_DOCUMENT:
            break;

        case XMLStreamConstants.END_ELEMENT:
            current = current.getParentElement();
            break;

        case XMLStreamConstants.ENTITY_DECLARATION:
        case XMLStreamConstants.NOTATION_DECLARATION:
            /* Shouldn't really get these, but maybe some stream readers
             * do provide the info. If so, better ignore it -- DTD event
             * should have most/all we need.
             */
            break;

        case XMLStreamConstants.ENTITY_REFERENCE:
            child = new EntityRef(r.getLocalName());
            break;

        case XMLStreamConstants.PROCESSING_INSTRUCTION:
            child = new ProcessingInstruction(r.getPITarget(), r.getPIData());
            break;

        case XMLStreamConstants.START_ELEMENT:
            // Ok, need to add a new element...
            {
                Element newElem = null;
                String nsURI = r.getNamespaceURI();
                String elemPrefix = r.getPrefix(); // needed for special handling of elem's namespace
                String ln = r.getLocalName();

                if (nsURI == null || nsURI.length() == 0) {
                    if (elemPrefix == null || elemPrefix.length() == 0) {
                        newElem = new Element(ln);
                    } else {
                        /* Happens when a prefix is bound to the default
                         * (empty) namespace...
                         */
                        newElem = new Element(ln, elemPrefix, "");
                    }
                } else {
                    newElem = new Element(ln, elemPrefix, nsURI);
                }

                /* Let's add element right away (probably have to do
                 * it to bind attribute namespaces, too)
                 */
                if (current == null) { // at root
                    doc.setRootElement(newElem);
                } else {
                    current.addContent(newElem);
                }

                // Any declared namespaces?
                for (int i = 0, len = r.getNamespaceCount(); i < len; ++i) {
                    String prefix = r.getNamespacePrefix(i);
                    if (prefix == null) {
                        prefix = "";
                    }
                    Namespace ns = Namespace.getNamespace(prefix, r.getNamespaceURI(i));

                    // JDOM has special handling for element's "own" ns:
                    if (prefix.equals(elemPrefix)) {
                        ; // already set by when it was constructed...
                    } else {
                        newElem.addNamespaceDeclaration(ns);
                    }
                }

                // And then the attributes:
                for (int i = 0, len = r.getAttributeCount(); i < len; ++i) {
                    String prefix = r.getAttributePrefix(i);
                    Namespace ns;

                    if (prefix == null || prefix.length() == 0) {
                        // Attribute not in any namespace
                        ns = Namespace.NO_NAMESPACE;
                    } else {
                        ns = newElem.getNamespace(prefix);
                    }
                    Attribute attr = new Attribute
                        (r.getAttributeLocalName(i),
                         r.getAttributeValue(i),
                         ns);
                    newElem.setAttribute(attr);
                }
                // And then 'push' new element...
                current = newElem;
            }
                   
            break;

        case XMLStreamConstants.DTD:
            /* !!! Note: StAX does not expose enough information about
             *  doctype declaration (specifically, public and system id!);
             *  should (re-)parse information... not yet implemented
             */
            // TBI
            break;

            // Should never get these, from a stream reader:
                   
            /* (commented out entries are just FYI; default catches
             * them all)
             */

            //case XMLStreamConstants.ATTRIBUTE:
            //case XMLStreamConstants.NAMESPACE:
        default:
            throw new RuntimeException("Unrecognized iterator event type: "+r.getEventType()+"; should not receive such types (broken stream reader?)");
        }

        if (child != null) {
            if (current == null) {
                doc.addContent(child);
            } else {
                current.addContent(child);
            }
        }
    }
    
    public void reset() {
        current = null;
        doc = null;
    }
}


/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
