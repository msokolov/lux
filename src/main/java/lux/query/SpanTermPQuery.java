package lux.query;

import lux.index.IndexConfiguration;
import lux.xml.QName;
import lux.xquery.ElementConstructor;

import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.ext.ExtendableQueryParser;

/**
 * Extends TermPQuery for use in contexts where a SpanTerm is required
 */
public class SpanTermPQuery extends TermPQuery {

    public static final QName SPAN_TERM_QNAME = new QName("SpanTerm");
    public static final QName REGEXP_TERM_QNAME = new QName("RegexpQuery");

    public SpanTermPQuery(Term t) {
        super(t);
    }
    
    @Override
    public ElementConstructor toXmlNode (String field, IndexConfiguration config) {
        if (config.isOption(IndexConfiguration.INDEX_EACH_PATH)) {
            Term wildTerm = new Term (getTerm().field(), 
            		ExtendableQueryParser.escape(getTerm().text()) + "(/.*)?");
            return new TermPQuery(wildTerm, getBoost()).toXmlNode(field, REGEXP_TERM_QNAME);
        }
        return toXmlNode(field, SPAN_TERM_QNAME);
    }

    @Override 
    public boolean isSpanCompatible() {
    	return true;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
