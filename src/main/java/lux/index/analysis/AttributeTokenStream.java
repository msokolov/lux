package lux.index.analysis;

import java.util.Iterator;

import lux.index.IndexConfiguration;
import lux.index.attribute.QNameAttribute;
import lux.xml.Offsets;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.apache.lucene.analysis.LowerCaseFilter;

/**
 * A TokenStream that extracts words from attributes in a Saxon Document model (XdmNode)
 */
public final class AttributeTokenStream extends TextOffsetTokenStream {

    private final QNameAttribute qnameAtt = addAttribute(QNameAttribute.class);

    public AttributeTokenStream(XdmNode doc, Offsets offsets) {
        super(doc, offsets);
        wrapped = new QNameTokenFilter(new LowerCaseFilter(IndexConfiguration.LUCENE_VERSION, tokenizer));
        contentIter = new ContentIterator(doc);
    }

    @Override
    protected void updateNodeAtts() {
        getAttributeQName();
    }
    
    private void getAttributeQName() {
        qnameAtt.clearQNames();
        QName qname = curNode.getNodeName();
        qnameAtt.addQName(new lux.xml.QName(qname.getNamespaceURI(), qname.getLocalName()));
    }

    /**
     * Iterates over /descendant::element()/@*); all descendant elements'
     * attributes TODO: refactor the lookahead logic
     */
    private static class ContentIterator implements Iterator<XdmNode> {

        private XdmSequenceIterator descendants;
        private XdmSequenceIterator attributes;
        private XdmNode next = null; // storage for lookahead

        protected ContentIterator(XdmNode node) {
            descendants = node.axisIterator(Axis.DESCENDANT_OR_SELF);
            attributes = EMPTY;
            next = null;
        }

        public boolean hasNext() {
            if (next != null) {
                return true;
            }
            next = getNext();
            return next != null;
        }

        private XdmNode getNext() {
            for (;;) {
                if (attributes.hasNext()) {
                    next = (XdmNode) attributes.next();
                    return next;
                } else if (descendants.hasNext()) {
                    XdmNode node = (XdmNode) descendants.next();
                    attributes = node.axisIterator(Axis.ATTRIBUTE);
                } else {
                    return null;
                }
            }
        }

        public XdmNode next() {
            if (next != null) {
                XdmNode node = next;
                next = null;
                return node;
            }
            return getNext();
        }

        public void remove() {
        }

    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
