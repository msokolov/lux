package lux.index.analysis;

import java.io.IOException;

import org.apache.lucene.analysis.TokenStream;

final class EmptyTokenStream extends TokenStream {

    public EmptyTokenStream(TokenStream source) {
        super (source);
    }
    
    @Override
    public boolean incrementToken() throws IOException {
        return false;
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
