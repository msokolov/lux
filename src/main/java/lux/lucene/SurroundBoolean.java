package lux.lucene;

import org.apache.lucene.search.BooleanClause.Occur;

/**
 * simplified BooleanQuery model for the surround query parser
 * - all clauses have the same occur value, which must be AND
 * or OR, not NOT.
 */
public class SurroundBoolean extends ParseableQuery {
    private ParseableQuery queries[];
    private Occur occur;
    
    public SurroundBoolean (Occur occur, ParseableQuery ... queries) {
        this.queries = queries;
        this.occur = occur;
    }

    public String toXml (String field) {
        StringBuilder buf = new StringBuilder("<BooleanQuery>");
        for (ParseableQuery q : queries) {
            buf.append ("<Clause occur=\"").append (occur).append("\">");
            buf.append (q.toXml(field));
            buf.append("</Clause>");
        }
        buf.append ("</BooleanQuery>");
        return buf.toString();
    }
    
    public String toString(String field) {
        StringBuilder buf = new StringBuilder();
        if (queries.length > 0) {
            buf.append(queries[0].toString());
        }
        String operator = occur == Occur.MUST ? " AND " : " OR ";
        for (int i = 1; i < queries.length; i++) {
            buf.append (operator);
            buf.append (queries[i].toString());
        }
        return buf.toString();
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
