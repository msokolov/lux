package lux.index.analysis;

import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;

import lux.xml.Offsets;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;

/**
 * A TokenStream that extracts words from attributes in a Saxon Document model (XdmNode)
 */
public final class AttributeTokenStream extends TextOffsetTokenStream {

    public AttributeTokenStream(String fieldName, Analyzer analyzer, TokenStream wrapped, XdmNode doc, Offsets offsets, Processor processor) {
        super(fieldName, analyzer, wrapped, doc, offsets, processor);
        contentIter = new AttributeIterator(doc);
    }
    
    @Override
    public void reset (Reader reader) throws IOException {
        super.reset(reader);
        qnameTokenFilter.reset(getWrappedTokenStream());  
        setWrappedTokenStream (qnameTokenFilter);
    }
    
    @Override
    protected boolean updateNodeAtts() {
        // TODO: hide attribute content of hidden elements by returning false?
        getAttributeQName();
        return true;
    }
    
    private void getAttributeQName() {
        qnameAtt.clearQNames();
        QName qname = curNode.getNodeName();
        qnameAtt.addQName(new lux.xml.QName(qname.getNamespaceURI(), qname.getLocalName(), qname.getPrefix()));
    }

    /**
     * Iterates over /descendant::element()/@*); all descendant elements'
     * attributes
     */
    private static class AttributeIterator implements Iterator<XdmNode> {

        private XdmSequenceIterator descendants;
        private XdmSequenceIterator attributes;
        private XdmNode next = null; // storage for lookahead

        protected AttributeIterator(XdmNode node) {
            descendants = node.axisIterator(Axis.DESCENDANT_OR_SELF);
            attributes = EMPTY;
            next = null;
        }

        @Override
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

        @Override
        public XdmNode next() {
            if (hasNext()) {
                XdmNode node = next;
                next = null;
                return node;
            }
            return null;
        }

        @Override
        public void remove() {
        }

    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
