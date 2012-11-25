package lux.index.analysis;

import java.io.IOException;

import lux.index.XPathValueMapper;

public final class QNameValueTokenStream extends ValueTokenStream {
    
    private static final int HASH_SIZE = XPathValueMapper.HASH_SIZE;
    private int bufpos;
    private char[] buf;
    
    public QNameValueTokenStream (Iterable<char[]> values) {
        super (values);
        buf = new char[HASH_SIZE];
    }
        
    protected boolean advanceValue () {
        if (!super.advanceValue())
            return false;
        pos = value.length - HASH_SIZE - 2;
        while (value[pos] != ' ') { 
            --pos;
        }
        bufpos = 0;
        return true;
    }
    
    @Override
    public boolean incrementToken() throws IOException {
        if (pos >= value.length) {
            if (!advanceValue()) {
                return false;
            }
        }
        System.arraycopy(value, value.length - HASH_SIZE, buf, 0, HASH_SIZE);
        while (value[pos] != ' ') {
            buf[bufpos] = (char) (buf[bufpos] * 15 + value[pos++]);
            if (++bufpos >= HASH_SIZE) {
                bufpos = 0;
            }
        }
        termAtt.copyBuffer(buf, 0, HASH_SIZE);
        // done - each value generates a single token only
        pos = value.length;
        return true;
    }
   
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
