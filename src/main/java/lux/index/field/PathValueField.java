package lux.index.field;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

import lux.index.WhitespaceGapAnalyzer;
import lux.index.XPathValueMapper;
import lux.index.XmlIndexer;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.document.Fieldable;

public class PathValueField extends XmlField {
    
    private static final PathValueField instance = new PathValueField();
    
    public static PathValueField getInstance() {
        return instance;
    }
    
    protected PathValueField () {
        super ("lux_path", new WhitespaceGapAnalyzer(), Store.NO, Type.TOKENS);
    }
    
    @Override
    public Iterable<Fieldable> getFieldValues(XmlIndexer indexer) {
        // replace with a custom Fieldable
        XPathValueMapper mapper = (XPathValueMapper) indexer.getPathMapper();        
        return new FieldValues (this, Collections.singleton
                (new TokenizedField(getName(), 
                        new PathValueTokenStream
                        (mapper.getPathValues()), Store.NO, Index.ANALYZED, TermVector.NO)));
    }
        
    class PathValueTokenStream extends TokenStream {

        private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
        private int pos = 0;
        private Iterable<char[]> values;
        private Iterator<char[]> valueIter;
        private char[] value;
        
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
        
        private boolean advanceValue () {
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

}
