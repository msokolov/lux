package lux.index.analysis;

import java.io.IOException;

import org.apache.lucene.analysis.CharStream;

public class CharSequenceStream extends CharStream {
    
    private int pos;
    private CharSequence csq;
    
    CharSequenceStream (CharSequence csq) {
        reset (csq);
    }

    void reset (CharSequence csq) {
        this.csq = csq;
        pos = 0;
    }
    
    @Override
    public int correctOffset(int currentOff) {
        return currentOff;
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
    public void close() throws IOException {
        csq = null;
    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */

