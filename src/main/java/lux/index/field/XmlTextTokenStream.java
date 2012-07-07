package lux.index.field;

import java.io.IOException;

import lux.index.XmlIndexer;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.apache.commons.io.input.CharSequenceReader;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * TODO: wrap an entire Analyzer, not just a StandardTokenizer
 */
final class XmlTextTokenStream extends TokenStream {
    
    private CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    
    //private int textNodeOffset;
    private XdmNode curNode;
    private TextIterator contentIter;
    private Tokenizer tokenizer;
    private TokenStream tokenStream;
    
    // TODO: change to QNameTextTokenStream(Analyzer)
    // and provide read (XdmNode)
    XmlTextTokenStream (XdmNode doc) {
        tokenizer = new StandardTokenizer(XmlIndexer.LUCENE_VERSION, this, new CharSequenceReader(""));
        tokenStream =new LowerCaseFilter(XmlIndexer.LUCENE_VERSION, tokenizer);
        contentIter = new TextIterator(doc);
    }

    private boolean advanceToTokenNode () {
        while (contentIter.hasNext()) {
            curNode = (XdmNode) contentIter.next();
            if (curNode == null) {
                // ContentIterator reports hasNext() == true but returns null when there are descendant nodes,
                // but no content
                return false;
            }
            // wrap the content in a reader and hand it to the tokenizer
            NodeInfo nodeInfo = curNode.getUnderlyingNode();
            if (resetTokenizer(nodeInfo.getStringValueCS())) {
                return true;
            }
        }
        return false;
    }
    
    private boolean resetTokenizer (CharSequence cs) {
        CharSequenceReader reader = new CharSequenceReader(cs);
        try {
            return resetTokenizer(reader);
        } catch (IOException e) {
            // don't expect this from a CharSequenceReader
        }
        return false;
    }
    
    /*
     * Advance the iteration by looping through the following:
     * 1) next text node
     * 2) next token in text
     * @see org.apache.lucene.analysis.TokenStream#incrementToken()
     */
    @Override
    public boolean incrementToken() throws IOException {
        if (!incrementTokenStream()) {            // next token in current node
            if (!advanceToTokenNode()) {        // next node with a token
                return false;
            }            
        }
        return true;
    }    
    
    private boolean incrementTokenStream() throws IOException {
        while (tokenStream.incrementToken()) {
            if (termAtt.length() > 0) {
                return true;
            }
        }
        return false;
    }
    
    private boolean resetTokenizer (CharSequenceReader reader) throws IOException {
        try {
            tokenizer.reset(reader);
        } catch (IOException e) {
            // CharSequenceReader won't throw an IO Exception
        }
        return incrementTokenStream();        
    }

    /**
     * Iterates over //text(); all descendant text nodes
     */
    private static class TextIterator {
        
        private XdmSequenceIterator descendants;
        
        protected TextIterator(XdmNode node) {
            descendants = node.axisIterator(Axis.DESCENDANT);
        }

        public boolean hasNext () {
            return descendants.hasNext();
        }
        
        public XdmNode next () {
            while (descendants.hasNext()) {
                XdmNode node = (XdmNode) descendants.next();
                if (node.getNodeKind() == XdmNodeKind.TEXT) {
                    return node;
                }
            }
            return null;
        }
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
