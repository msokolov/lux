package lux.lucene;

import org.apache.lucene.index.Term;

/**
 * Surround parser doesn't support multiple fields in a query?  This term
 * query simply suppresses its field in toString() so its output can be parsed by
 * the surround parser.
 *
 */
public class SurroundTerm extends LuxTermQuery {

    public SurroundTerm(Term t) {
        super(t);
    }

    @Override
    public String toString (String field) {
        return LuxTermQuery.toString(field, getTerm(), getBoost());
    }
    

    public String toXml (String field) {
        StringBuilder buffer = new StringBuilder("<SpanTerm");
        appendContents(field, buffer);
        buffer.append("</SpanTerm>");
        return buffer.toString();
    }
    

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
