package lux.lucene;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.Query;

public class SurroundBoolean extends Query {
    private Query queries[];
    private String operator;
    
    public SurroundBoolean (Occur occur, Query ... queries) {
        this.queries = queries;
        this.operator = occur == Occur.MUST ? " AND " : " OR ";
    }

    @Override
    public String toString(String field) {
        StringBuilder buf = new StringBuilder();
        if (queries.length > 0) {
            buf.append(queries[0].toString());
        }
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
