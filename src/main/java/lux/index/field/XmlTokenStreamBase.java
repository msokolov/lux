package lux.index.field;

import java.io.IOException;
import java.util.Iterator;

import lux.index.XmlIndexer;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.apache.commons.io.input.CharSequenceReader;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * TODO: wrap an entire Analyzer, not just a StandardTokenizer
 */
abstract class XmlTokenStreamBase extends TokenStream {

  protected XdmNode curNode;
  protected Iterator<XdmNode> contentIter;

  protected CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);

  protected OffsetCharStream charStream = new OffsetCharStream(null);
  protected Tokenizer tokenizer;
  protected TokenStream tokenStream;

  protected static final XdmSequenceIterator EMPTY = new EmptyXdmIterator (null);

  // TODO: change to QNameTextTokenStream(Analyzer)
  // and provide read (XdmNode)
  XmlTokenStreamBase (XdmNode doc) {
    tokenizer = new StandardTokenizer(XmlIndexer.LUCENE_VERSION, this, new CharSequenceReader(""));
  }

  protected boolean incrementTokenStream() throws IOException {
    while (tokenStream.incrementToken()) {
      if (termAtt.length() > 0) {
        return true;
      }
    }
    return false;
  }
  
  protected boolean advanceToTokenNode () {
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
  
  abstract boolean resetTokenizer (CharSequence cs);
  
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
