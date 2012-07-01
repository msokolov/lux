package lux.index.field;

import java.io.IOException;

import lux.index.XmlIndexer;
import net.sf.saxon.expr.parser.Token;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.pattern.CombinedNodeTest;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.apache.commons.io.input.CharSequenceReader;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.util.AttributeImpl;

/**
 * TODO: wrap an entire Analyzer, not just a StandardTokenizer
 */
final class QNameTextTokenStream extends TokenStream {
    
    private final QNameAttribute qnameAtt = addAttribute(QNameAttribute.class);
    private CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    
    //private int textNodeOffset;
    private XdmNode curNode;
    private ContentIterator contentIter;
    private Tokenizer tokenizer;
    private TokenStream tokenStream;
    private static final XdmSequenceIterator EMPTY = new EmptyXdmIterator (null);
    
    // TODO: change to QNameTextTokenStream(Analyzer)
    // and provide read (XdmNode)
    QNameTextTokenStream (XdmNode doc) {
        tokenizer = new StandardTokenizer(XmlIndexer.LUCENE_VERSION, this, new CharSequenceReader(""));
        tokenStream = new QNameTokenFilter (new LowerCaseFilter(XmlIndexer.LUCENE_VERSION, tokenizer));
        contentIter = new ContentIterator(doc);
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
     * 3) next ancestor element node
     * @see org.apache.lucene.analysis.TokenStream#incrementToken()
     */
    @Override
    public boolean incrementToken() throws IOException {
        if (!incrementTokenStream()) {            // next token in current node
            if (!advanceToTokenNode()) {        // next node with a token
                return false;
            }            
            getAncestorQNames();
        }
        return true;
    }

    private void getAncestorQNames() {
        AncestorIterator nodeAncestors = new AncestorIterator(curNode);
        qnameAtt.getQNames().clear();
        while (nodeAncestors.hasNext()) {
            XdmNode e = (XdmNode) nodeAncestors.next();
            if (e.getNodeKind() == XdmNodeKind.ELEMENT || e.getNodeKind() == XdmNodeKind.ATTRIBUTE) {
                QName qname = e.getNodeName();
                String localName = qname.getLocalName();
                if (e.getNodeKind() == XdmNodeKind.ATTRIBUTE) {
                    localName = '@' + localName;
                }
                qnameAtt.addQName(new lux.xpath.QName(qname.getNamespaceURI(), localName));
            }
        }
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
        resetCounters ();
        return incrementTokenStream();        
    }
    
    private void resetCounters () {
        // textNodeCounter = 0;
        // textNodeOffset = offsets[textNodeCounter];        
    }
    
    private static class EmptyXdmIterator extends XdmSequenceIterator {

        protected EmptyXdmIterator(SequenceIterator<?> base) {
            super(base);
        }
        
        public boolean hasNext () {
            return false;
        }
        
        public XdmItem next () {
            return null;
        }
        
    }
    
    /**
     * iterates over ancestor-or-self::node()[self::text() or self::element() or self::attribute()]
     * @author sokolov
     *
     */
    private static class AncestorIterator extends XdmSequenceIterator {

        protected AncestorIterator(XdmNode node) {
            super(node.getUnderlyingNode().iterateAxis(Axis.ANCESTOR_OR_SELF.getAxisNumber(), 
                    new CombinedNodeTest (NodeKindTest.TEXT, Token.UNION,
                            new CombinedNodeTest (NodeKindTest.ELEMENT, Token.UNION, NodeKindTest.ATTRIBUTE))));
        }
        
    }
    
    /**
     * Iterates over (//text() | /descendant::element()/@*) 
     * all descendant text nodes and all descendant elements' attributes
     *
     * If the node has no text and no attribute descendants, but has other 
     *  descendant nodes, we report hasNext() = true, yet return null from next().
     */
    private static class ContentIterator {
        
        private XdmSequenceIterator descendants;
        private XdmSequenceIterator attributes;
        
        protected ContentIterator(XdmNode node) {
            descendants = node.axisIterator(Axis.DESCENDANT_OR_SELF);
            attributes = EMPTY;
        }

        public boolean hasNext () {
            if (! attributes.hasNext()) {
                if (! descendants.hasNext()) {
                    return false;
                }
            }
            return true;
        }
        
        public XdmNode next () {
            OUTER:
            for (;;) {
                if (attributes.hasNext()) {
                    return (XdmNode) attributes.next();
                }
                while (descendants.hasNext()) {
                    XdmNode node = (XdmNode) descendants.next();
                    if (node.getNodeKind() == XdmNodeKind.TEXT) {
                        return node;
                    }
                    if (node.getNodeKind() == XdmNodeKind.ELEMENT) {
                        attributes = node.axisIterator(Axis.ATTRIBUTE);
                        continue OUTER; // tail recurse - would the compiler do this for us?
                    }
                }
                return null;
            }
        }
        
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
