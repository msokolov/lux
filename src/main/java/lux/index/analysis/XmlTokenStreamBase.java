package lux.index.analysis;

import java.io.IOException;
import java.util.Iterator;

import lux.index.IndexConfiguration;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.apache.commons.io.input.CharSequenceReader;
import org.apache.lucene.analysis.CharStream;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * TODO: wrap an entire Analyzer, not just a StandardTokenizer
 */
abstract class XmlTokenStreamBase extends TokenStream {

    protected TokenStream wrapped; // these two do the actual tokenizing within
                                   // each block of text
    protected Tokenizer tokenizer;

    protected XdmNode curNode;
    protected Iterator<XdmNode> contentIter; // retrieves the nodes with text to
                                             // index

    protected CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

    protected CharStream charStream = new OffsetCharFilter(null);

    protected static final XdmSequenceIterator EMPTY = new EmptyXdmIterator(null);

    // TODO: change to QNameTextTokenStream(Analyzer)
    // and provide read (XdmNode)
    XmlTokenStreamBase(XdmNode doc) {
        tokenizer = new StandardTokenizer(IndexConfiguration.LUCENE_VERSION, this, new CharSequenceReader(""));
    }
    
    /*
     * Advance the iteration by looping through the following:
     * 1) next text node
     * 2) next token in text
     * 3) next ancestor element node
     * @see org.apache.lucene.analysis.TokenStream#incrementToken()
     */
    @Override
    public boolean incrementToken() throws IOException {
        if (!incrementWrappedTokenStream()) {            // next token in current node
            if (!advanceToTokenNode()) {        // next node with a token
                return false;
            }
        }
        return true;
    }
    
    protected boolean incrementWrappedTokenStream() throws IOException {
        while (wrapped.incrementToken()) {
            if (termAtt.length() > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean advanceToTokenNode() {
        while (contentIter.hasNext()) {
            curNode = (XdmNode) contentIter.next();            
            // wrap the content in a reader and hand it to the tokenizer
            NodeInfo nodeInfo = curNode.getUnderlyingNode();
            updateNodeAtts ();
            if (resetTokenizer(nodeInfo.getStringValueCS())) {
                return true;
            }
        }
        return false;
    }

    abstract boolean resetTokenizer(CharSequence cs);

    abstract void updateNodeAtts ();
}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
