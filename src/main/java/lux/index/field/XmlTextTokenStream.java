package lux.index.field;

import java.io.IOException;
import java.util.Iterator;

import lux.index.XmlIndexer;

import org.apache.lucene.analysis.LowerCaseFilter;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;

/**
 * TODO: wrap an entire Analyzer, not just a StandardTokenizer
 */
final class XmlTextTokenStream extends TextTokenStreamBase {
    
    XmlTextTokenStream (XdmNode doc, int[] textOffsets) {
      super (doc, textOffsets);
      contentIter = new TextIterator(doc);
      tokenStream = new LowerCaseFilter(XmlIndexer.LUCENE_VERSION, tokenizer);

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
    
    protected boolean resetTokenizer (CharSequence text) {
        charStream.reset(text);
        try {
          tokenizer.reset(charStream);
          if (curNode.getNodeKind() == XdmNodeKind.TEXT) {
            charStream.setCharStreamOffset(textOffsets[iText++]);
          }       
          return incrementTokenStream(); 
        } catch (IOException e) {
          return false;
        }
      }
    
    /**
     * Iterates over //text(); all descendant text nodes
     */
    private static class TextIterator implements Iterator<XdmNode> {
        
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

        public void remove() {
        }
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
