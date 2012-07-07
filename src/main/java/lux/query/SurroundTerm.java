package lux.query;

import lux.xpath.QName;
import lux.xquery.ElementConstructor;

import org.apache.lucene.index.Term;

/**
 * Surround parser doesn't support multiple fields in a query?  This term
 * query simply suppresses its field in toString() so its output can be parsed by
 * the surround parser.
 *
 */
public class SurroundTerm extends TermPQuery {

    private static final QName SPAN_TERM_QNAME = new QName("SpanTerm");

    public SurroundTerm(Term t) {
        super(t);
    }
    
    public ElementConstructor toXmlNode (String field) {
        return toXmlNode("", SPAN_TERM_QNAME);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
