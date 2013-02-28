package lux.index.analysis;

import java.io.IOException;
import java.io.Reader;

public class CharSequenceStream extends Reader {
    
    private int pos;
    private CharSequence csq;
    
    CharSequenceStream (CharSequence csq) {
        reset (csq);
    }

    void reset (CharSequence c) {
        this.csq = c;
        pos = 0;
    }
    
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

