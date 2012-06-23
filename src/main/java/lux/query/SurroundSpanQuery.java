package lux.query;

/**
 * Model a SpanNearQuery
 */
public class SurroundSpanQuery extends ParseableQuery {
    
    private ParseableQuery[] clauses;
    private int slop;
    private boolean inOrder;

    public SurroundSpanQuery(int slop, boolean inOrder, ParseableQuery ... clauses) {
        this.clauses = clauses;
        this.slop= slop;
        this.inOrder= inOrder;
    }
    
    public String toXmlString (String field) {
        StringBuilder buf = new StringBuilder("<SpanNear");
        if (inOrder) {
            buf.append (" inOrder=\"true\"");
        }
        buf.append (" slop=\"").append(slop).append('"');
        buf.append ('>');
        for (ParseableQuery q : clauses) {
            buf.append(q.toXmlString(field));
        }
        buf.append ("</SpanNear>");
        return buf.toString();
    }
    
    @Override
    public String toString () {
        StringBuilder buf = new StringBuilder();
        if (slop > 0) {
            buf.append(slop+1);
        }
        if (inOrder) {
            buf.append ('w');
        } else {
            buf.append ('n');
        }
        buf.append ('(');
        if (clauses.length > 0) {
            buf.append (clauses[0].toString());            
        }
        for (int i = 1; i < clauses.length; i++) {
            buf.append (",");
            buf.append (clauses[i].toString());
        }
        buf.append (')');
        return buf.toString();
    }

    public String toString(String field) {
        return field + ":(" + toString() + ')';
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
