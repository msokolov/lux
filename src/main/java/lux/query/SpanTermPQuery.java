package lux.query;

import lux.xml.QName;
import lux.xquery.ElementConstructor;

import org.apache.lucene.index.Term;

/**
 * Extends TermPQuery for use in contexts where a SpanTerm is required
 */
public class SpanTermPQuery extends TermPQuery {

    private static final QName SPAN_TERM_QNAME = new QName("SpanTerm");

    public SpanTermPQuery(Term t) {
        super(t);
    }
    
    public ElementConstructor toXmlNode (String field) {
        return toXmlNode(field, SPAN_TERM_QNAME);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
