package lux.search.highlight;

import java.io.IOException;
import java.util.ArrayList;

import lux.xml.QName;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttributeImpl;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

/**
 * Wraps a TokenStream, modifying its CharTermAttribute so as to return
 * the original term, and the term prefixed by each of its enclosing element's QNames,
 * in turn.
 */
public final class StreamingElementTokens extends TokenStream {
    
    private TokenStream wrapped;
    private final CharTermAttribute termAtt;
    private final PositionIncrementAttribute posIncrAtt;
    private final CharTermAttribute term;
    
    private ArrayList<QName> qnames;
    
    // iterate over the qnames; qnamePos = qnames.size() -> no QName
    // qnamePos = -1 -> done iterating
    private int qnamePos = -1;
    
    /**
     * @return true if this token represents the token from the XML_TEXT field,
     * and is not prefixed by an element QName
     */
    public boolean isPlainToken () {
        return termAtt.length() == term.length();
    }
    
    public StreamingElementTokens (TokenStream tokens) {
        super (tokens); // share the same attributes
        wrapped = tokens;
        termAtt = addAttribute(CharTermAttribute.class);
        posIncrAtt = addAttribute(PositionIncrementAttribute.class);
        term = new CharTermAttributeImpl();
        qnames = new ArrayList<QName>();
    }
    
    public void pushElement (QName qname) {
        qnames.add(qname);
    }
    
    public void popElement () {
        qnames.remove(qnames.size()-1);
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (qnamePos < 0) {
            if (!wrapped.incrementToken()) {
                return false;
            }
            qnamePos = qnames.size() - 1;
            term.setEmpty();
            term.append(termAtt);
            return true;
        }
        QName qname = qnames.get(qnamePos--);
        termAtt.setEmpty();
        termAtt.append (qname.getEncodedName());
        termAtt.append (':');
        termAtt.append (term);
        posIncrAtt.setPositionIncrement(0);
        return true;
    }

    public void reset (TokenStream tokens) throws IOException {
        wrapped = tokens;
        reset ();
    }
    
    @Override public void reset () throws IOException {
        super.reset();
        wrapped.reset();
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
