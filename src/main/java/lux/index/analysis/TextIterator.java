package lux.index.analysis;

import java.util.Iterator;

import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;

/**
 * Iterates over //text(); all descendant text nodes
 */
public class TextIterator implements Iterator<XdmNode> {
        
    private XdmSequenceIterator descendants;
    private XdmNode next = null;  // storage for lookahead
        
    public TextIterator(XdmNode node) {
        descendants = new TextNodeDescendantIterator(node);
    }

    @Override
    public boolean hasNext () {
        if (next != null) {
            return true;
        }
        next = getNext();
        return next != null;
    }
        
    @Override
    public XdmNode next () {
        if (next != null) {
            XdmNode node = next;
            next = null;
            return node;
        }
        return getNext ();
    }
        
    protected XdmNode getNext () {
        while (descendants.hasNext()) {
            return (XdmNode) descendants.next();
        }
        return null;
    }

    @Override
    public void remove() {
    }
    
    class TextNodeDescendantIterator extends XdmSequenceIterator {

        protected TextNodeDescendantIterator(XdmNode node) {
            super (node.getUnderlyingNode().iterateAxis(Axis.DESCENDANT_OR_SELF.getAxisNumber(),  NodeKindTest.TEXT));
        }
        
    }
        
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
