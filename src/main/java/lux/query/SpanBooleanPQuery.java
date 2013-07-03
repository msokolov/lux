package lux.query;

import lux.index.IndexConfiguration;
import lux.xml.QName;
import lux.xpath.AbstractExpression;
import lux.xpath.Sequence;
import lux.xquery.ElementConstructor;

import org.apache.lucene.search.BooleanClause.Occur;

/**
 * simplified BooleanQuery model for use with Spans
 * - all clauses have the same occur value, which must be AND
 * or OR, not NOT.
 */
public class SpanBooleanPQuery extends BooleanPQuery {
    private static final QName SPAN_OR_QNAME = new QName("SpanOr");

    public SpanBooleanPQuery (Occur occur, ParseableQuery ... queries) {
        super (occur, queries);
    }
    
    public SpanBooleanPQuery (Clause ... clauses) {
        super (clauses);
    }
    
    @Override
    public ElementConstructor toXmlNode(String field, IndexConfiguration config) {
        if (getOccur().equals(Occur.MUST) || config.isOption(IndexConfiguration.INDEX_EACH_PATH)) {
            return super.toXmlNode(field, config);
        }
        Clause [] clauses = getClauses();
        AbstractExpression[] clauseExprs = new AbstractExpression[clauses.length];
        for (int i = 0; i < clauses.length; i++) {
            clauseExprs [i] = clauses[i].getQuery().toXmlNode(field, config);
        }
        return new ElementConstructor (SPAN_OR_QNAME, new Sequence(clauseExprs));
    }
    
    @Override
    public String toQueryString(String field, IndexConfiguration config) {
        if (getOccur().equals(Occur.MUST) || config.isOption(IndexConfiguration.INDEX_EACH_PATH)) {
            return super.toQueryString(field, config);
        }
        StringBuilder buf = new StringBuilder();
        buf.append("(lux_within:0");
        for (Clause clause : getClauses()) {
            buf.append(' ').append (clause.getQuery().toQueryString(field, config));
        }
        buf.append(')');
        return buf.toString();
    }
    
    @Override
    public boolean isSpan() {
    	return true;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
