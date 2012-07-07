package lux.query;

import lux.xpath.AbstractExpression;
import lux.xpath.QName;
import lux.xpath.Sequence;
import lux.xquery.ElementConstructor;

import org.apache.lucene.search.BooleanClause.Occur;

/**
 * simplified BooleanQuery model for the surround query parser
 * - all clauses have the same occur value, which must be AND
 * or OR, not NOT.
 */
public class SurroundBoolean extends BooleanPQuery {
    private static final QName SPAN_OR_QNAME = new QName("SpanOr");

    public SurroundBoolean (Occur occur, ParseableQuery ... queries) {
        super (occur, queries);
    }
    
    public ElementConstructor toXmlNode(String field) {
        if (getOccur().equals(Occur.MUST)) {
            return super.toXmlNode(field);
        }        
        Clause [] clauses = getClauses();
        if (clauses.length == 1) {
            // TODO: handle Occur.MUST_NOT
            if (getOccur().equals(Occur.MUST_NOT)) {
                throw new UnsupportedOperationException("SurroundBoolean doesn't support MUST_NOT");
            }
            return new ElementConstructor (SPAN_OR_QNAME, clauses[0].getQuery().toXmlNode(field));
        }
        AbstractExpression[] clauseExprs = new AbstractExpression[clauses.length];
        int i = 0;
        for (Clause q : clauses) {
            clauseExprs [i++] = q.getQuery().toXmlNode(field);
        }
        return new ElementConstructor (SPAN_OR_QNAME, new Sequence(clauseExprs));
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
