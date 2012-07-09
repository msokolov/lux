package lux.index.analysis;

import java.util.Iterator;

import lux.index.XmlIndexer;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.apache.lucene.analysis.LowerCaseFilter;

/**
 * TODO: wrap an entire Analyzer, not just a StandardTokenizer
 */
public final class XmlTextTokenStream extends TextOffsetTokenStream {

    public XmlTextTokenStream(XdmNode doc, Offsets offsets) {
        super(doc, offsets);
        contentIter = new TextIterator(doc);
        wrapped = new LowerCaseFilter(XmlIndexer.LUCENE_VERSION, tokenizer);
    }

    @Override
    void updateNodeAtts() {
    }

    /**
     * Iterates over //text(); all descendant text nodes
     */
    private static class TextIterator implements Iterator<XdmNode> {

        private XdmSequenceIterator descendants;

        protected TextIterator(XdmNode node) {
            descendants = node.axisIterator(Axis.DESCENDANT);
        }

        public boolean hasNext() {
            return descendants.hasNext();
        }

        public XdmNode next() {
            while (descendants.hasNext()) {
                XdmNode node = (XdmNode) descendants.next();
                if (node.getNodeKind() == XdmNodeKind.TEXT) {
                    return node;
                }
            }
            return null;
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
