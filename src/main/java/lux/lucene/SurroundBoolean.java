package lux.lucene;

import org.apache.lucene.search.BooleanClause.Occur;

/**
 * simplified BooleanQuery model for the surround query parser
 * - all clauses have the same occur value, which must be AND
 * or OR, not NOT.
 */
public class SurroundBoolean extends BooleanPQuery {
    
    public SurroundBoolean (Occur occur, ParseableQuery ... queries) {
        super (occur, queries);
    }
    
    public String toString(String field) {
        StringBuilder buf = new StringBuilder();
        Clause [] clauses = getClauses();
        if (clauses.length > 0) {
            buf.append(clauses[0].getQuery().toString());
        }
        String operator = getOccur() == Occur.MUST ? " AND " : " OR ";
        for (int i = 1; i < clauses.length; i++) {
            buf.append (operator);
            buf.append (clauses[i].getQuery().toString());
        }
        return buf.toString();
    }
    
    public String toXml (String field) {
        if (getOccur().equals(Occur.MUST)) {
            return super.toXml(field);
        }        
        StringBuilder buf = new StringBuilder ("<SpanOr>");
        for (Clause clause : getClauses()) {
            buf.append (clause.getQuery().toXml(field));
        }
        buf.append ("</SpanOr>");
        return buf.toString();
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
