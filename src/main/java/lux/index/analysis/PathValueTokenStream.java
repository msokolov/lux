package lux.index.analysis;

import java.io.IOException;

import lux.index.XPathValueMapper;

public final class PathValueTokenStream extends ValueTokenStream {

    public PathValueTokenStream (Iterable<char[]> values) {
        super (values);
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

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
