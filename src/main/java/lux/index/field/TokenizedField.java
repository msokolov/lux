package lux.index.field;

import java.io.Reader;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.AbstractField;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;

public class TokenizedField extends AbstractField {
    
    private TokenStream tokenStream;
    
    TokenizedField (String name, TokenStream tokenStream, Store store, Index index, TermVector termVector) {
        super (name, store, index, termVector);
        this.tokenStream = tokenStream;
    }

    public String stringValue() {
        return null;
    }

    public Reader readerValue() {
        return null;
    }

    public TokenStream tokenStreamValue() {
        return tokenStream;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
