package lux.index.field;

import java.io.IOException;

import org.apache.lucene.analysis.CharStream;

// TODO - pull out
public class OffsetCharStream extends CharStream {
    
    private int pos;
    private CharSequence csq;
    private int charStreamOffset;
    
    OffsetCharStream (CharSequence csq) {           
        setCharStreamOffset(0);
        reset (csq);
    }
    
    void reset (CharSequence csq) {
        this.csq = csq;
        pos = 0;
    }

    @Override
    public int correctOffset(int currentOff) {           
        return currentOff + charStreamOffset;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        int remain = csq.length() - pos;
        if (remain <= 0) {
            return -1;
        }
        int n = remain > len ? len : remain;
        int limit = pos + n;
        while (pos < limit) {
            cbuf[off++] = csq.charAt(pos++);                
        }
        return n;
    }

    @Override
    public void close() {
    }

    public int getCharStreamOffset() {
        return charStreamOffset;
    }

    public void setCharStreamOffset(int offset) {
        this.charStreamOffset = offset;
    }
    
}