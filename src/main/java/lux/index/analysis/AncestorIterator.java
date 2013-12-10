package lux.index.analysis;

import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;


/**
 * iterates over ancestor::*
 */
class AncestorIterator extends XdmSequenceIterator {

    protected AncestorIterator(XdmNode node) {
        super(node.getUnderlyingNode().iterateAxis(Axis.ANCESTOR.getAxisNumber(), NodeKindTest.ELEMENT));
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
