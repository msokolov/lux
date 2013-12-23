package lux.index.analysis;

import lux.xml.Offsets;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;

/**
 * A TokenStream that extracts text from a Saxon Document model (XdmNode) and generates
 * a token for every "word" for every element that contains it.
 * TODO: control over element transparency
 * 
 * <p>Each element name may be one of: transparent, opaque, hidden, or container.  The default may be
 *    set to either opaque or transparent. Unless hidden, text is tagged with its parent element. 
 *    If its parent is transparent, it is also tagged with ancestor elements, stopping at the first opaque 
 *    or container element. In addition, visible (non-hidden) text is tagged with all ancestor container elements.
 * </p>
 * <dl>
 *   <dt>{@link ElementVisibility#OPAQUE}</dt>
 *   <dd>text content is indexed only with the parent element tag. Opaque elements' start and end tags act as phrase boundaries.</dd>
 *   <dt>{@link ElementVisibility#TRANSPARENT}</dt>
 *   <dd>if the parent element is transparent, the text is also indexed as if it were a child of the grandparent element; and the same rule applies recursively.</dd>
 *   <dt>{@link ElementVisibility#HIDDEN}</dt>
 *   <dd>descendant content of hidden elements is not indexed.</dd>
 *   <dt>{@link ElementVisibility#CONTAINER}</dt>
 *   <dd>a container element tags all of its visible descendants, regardless of opacity.</dd>
 * </dl>
 */
public final class ElementTokenStream extends TextOffsetTokenStream {
    
    public ElementTokenStream(String fieldName, Analyzer analyzer, TokenStream wrapped, XdmNode doc, Offsets offsets, Processor processor) {
        super(fieldName, analyzer, wrapped, doc, offsets, processor);
        contentIter = new TextIterator(doc);
        setWrappedTokenStream (qnameTokenFilter);
    }
    
    @Override
    protected boolean updateNodeAtts () {
        getAncestorQNames();
        return qnameAtt.hasNext();
    }
    
    private void getAncestorQNames() {
        // list the QNames of containing elements in qnameAtt filtered by the visibility rules
        assert(curNode.getNodeKind() == XdmNodeKind.TEXT);
        AncestorIterator nodeAncestors = new AncestorIterator(curNode);
        qnameAtt.clearQNames();
        boolean isOpaque = false;
        while (nodeAncestors.hasNext()) {
            XdmNode e = (XdmNode) nodeAncestors.next();
            assert (e.getNodeKind() == XdmNodeKind.ELEMENT);
            int nameCode = e.getUnderlyingNode().getNameCode();
            ElementVisibility vis = eltVis.get(nameCode);
            if (vis == null) {
                // nothing configured for this QName, use the default visibility
                vis = defVis;
            }
            if (vis == ElementVisibility.HIDDEN) {
                // this node is hidden: don't index its content
                qnameAtt.clearQNames();
                return;
            }
            // TODO; avoid allocating all these QNames?
            QName qname = e.getNodeName();
            if (isOpaque) {
                // we hit an opaque element in a previous iteration, so this element can't "see" the content
                // unless it is a container, which sees through opaque elements
                if (vis == ElementVisibility.CONTAINER) {
                    qnameAtt.addQName(new lux.xml.QName(qname.getNamespaceURI(),  qname.getLocalName(), qname.getPrefix()));
                }
            } else {
                // all elements so far have been transparent, so tag the content with this element name
                qnameAtt.addQName(new lux.xml.QName(qname.getNamespaceURI(),  qname.getLocalName(), qname.getPrefix()));
                if (vis == ElementVisibility.OPAQUE || vis == ElementVisibility.CONTAINER) {
                    // set the opaque flag if this element is opaque (containers are always opaque).
                    // still continue, because there might be containers
                    isOpaque = true;
                }
            }
        }
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
