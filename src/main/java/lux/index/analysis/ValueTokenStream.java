package lux.index.analysis;

import java.util.Iterator;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

public abstract class ValueTokenStream extends TokenStream {

    protected final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    protected int pos = 0;
    protected char[] value;
    private Iterable<char[]> values;
    private Iterator<char[]> valueIter;

    protected ValueTokenStream (Iterable<char[]> values) {
        setValues (values);
    }

    protected void setValues(Iterable<char[]> values) {
        this.values = values;
        reset();
    }

    @Override
    public void reset() {
        valueIter = values.iterator();
        advanceValue();
    }

    protected boolean advanceValue() {
        pos = 0;
        if (valueIter.hasNext()) {
            value = valueIter.next();
            return true;
        } else {
            value = null;
            return false;
        }            
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
