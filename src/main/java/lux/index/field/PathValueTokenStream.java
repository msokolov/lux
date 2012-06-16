package lux.index.field;

import java.io.IOException;
import java.util.Iterator;

import lux.index.XPathValueMapper;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

class PathValueTokenStream extends TokenStream {

    protected final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    protected int pos = 0;
    protected char[] value;
    private Iterable<char[]> values;
    private Iterator<char[]> valueIter;
    
    PathValueTokenStream (Iterable<char[]> values) {
        setValues (values);
    }
    
    void setValues (Iterable<char[]> values) {
        this.values = values;
        reset();
    }
    
    @Override
    public void reset () {
        valueIter = values.iterator();
        advanceValue();
    }
    
    protected boolean advanceValue () {
        pos = 0;
        if (valueIter.hasNext()) {
            value = valueIter.next();
            return true;
        } else {
            value = null;
            return false;
        }            
    }

    @Override
    public boolean incrementToken() throws IOException {
        if (pos >= value.length) {
            if (!advanceValue()) {
                return false;
            }
        }
        if (pos >= value.length - XPathValueMapper.HASH_SIZE) {
            // on the final value token - *may contain spaces*
            termAtt.copyBuffer(value, pos, XPathValueMapper.HASH_SIZE);
            pos += XPathValueMapper.HASH_SIZE;
            return true;
        }
        // a path component, separated by whitespace
        int start = pos;
        while (value[pos] != ' ') {
            ++pos;
        }
        termAtt.copyBuffer(value, start, pos-start);
        ++pos; // skip over the space
        return true;
    }
    
}