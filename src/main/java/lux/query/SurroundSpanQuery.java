package lux.query;

import java.util.ArrayList;

import lux.index.field.XmlField;
import lux.xpath.AbstractExpression;
import lux.xpath.LiteralExpression;
import lux.xpath.QName;
import lux.xpath.Sequence;
import lux.xquery.AttributeConstructor;
import lux.xquery.ElementConstructor;

/**
 * Model a SpanNearQuery
 */
public class SurroundSpanQuery extends ParseableQuery {
    
    private static final LiteralExpression SLOP_ATT_NAME = new LiteralExpression("slop");
    private static final QName SPAN_NEAR_QNAME = new QName("SpanNear");
    private static final AttributeConstructor IN_ORDER_ATT = new AttributeConstructor(new LiteralExpression("inOrder"), new LiteralExpression("true"));
    private ParseableQuery[] clauses;
    private int slop;
    private boolean inOrder;

    public SurroundSpanQuery(int slop, boolean inOrder, ParseableQuery ... clauses) {
        if (slop == 0 && inOrder) {
            this.clauses = mergeSubClauses(clauses);
        } else {
            this.clauses = clauses;
        }
        this.slop= slop;
        this.inOrder= inOrder;
    }

    // optimize? by simplifying nested queries
    private ParseableQuery[] mergeSubClauses(ParseableQuery [] clauses) {
        ArrayList<ParseableQuery> subclauses = new ArrayList<ParseableQuery>();
        for (ParseableQuery clause : clauses) {
            if (clause instanceof SurroundSpanQuery) {
                SurroundSpanQuery subquery = (SurroundSpanQuery) clause;
                if (subquery.slop == 0 && subquery.inOrder) {
                    for (ParseableQuery subclause : subquery.clauses) {
                        subclauses.add(subclause);
                    }
                    continue;
                }
            }
            subclauses.add(clause);

        }
        return subclauses.toArray(new ParseableQuery[0]);
    }

    public String toXmlString (String field) {
        if (field == null) {
            field = XmlField.PATH.getName();
        }
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
    
    /**
     * Render the query in the Lucene surround query parser dialect with the given default field.
     * @param field the default field for the query
     */
    public String toString(String field) {
        if (field == null) {
            field = XmlField.PATH.getName();
        }
        boolean showField = !(field.isEmpty() || field.equals(XmlField.PATH.getName()));
        StringBuilder buf = new StringBuilder();
        if (showField) {
            buf.append(field).append(":(");
        }
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
            buf.append (clauses[0].toString(field));
        }
        for (int i = 1; i < clauses.length; i++) {
            buf.append (",");
            buf.append (clauses[i].toString(field));
        }
        buf.append (')');
        if (showField) {
            buf.append (')');
        }
        return buf.toString();
    }

    @Override
    public ElementConstructor toXmlNode(String field) {
        AttributeConstructor inOrderAtt=null, slopAtt;
        if (inOrder) {
            inOrderAtt = IN_ORDER_ATT;
        }
        slopAtt = new AttributeConstructor(SLOP_ATT_NAME, new LiteralExpression(slop));
        if (clauses.length == 1) {
            return new ElementConstructor (SPAN_NEAR_QNAME, clauses[0].toXmlNode(field), inOrderAtt, slopAtt);
        }
        AbstractExpression[] clauseExprs = new AbstractExpression[clauses.length];
        int i = 0;
        for (ParseableQuery q : clauses) {
            clauseExprs [i++] = q.toXmlNode(field);
        }
        return new ElementConstructor (SPAN_NEAR_QNAME, new Sequence(clauseExprs), inOrderAtt, slopAtt);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
