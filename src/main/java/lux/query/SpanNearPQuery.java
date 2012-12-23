package lux.query;

import java.util.ArrayList;

import lux.index.IndexConfiguration;
import lux.xml.QName;
import lux.xpath.AbstractExpression;
import lux.xpath.LiteralExpression;
import lux.xpath.Sequence;
import lux.xquery.AttributeConstructor;
import lux.xquery.ElementConstructor;

/**
 * Model a SpanNearQuery
 */
public class SpanNearPQuery extends ParseableQuery {
    
    private static final LiteralExpression SLOP_ATT_NAME = new LiteralExpression("slop");
    private static final QName SPAN_NEAR_QNAME = new QName("SpanNear");
    private static final AttributeConstructor IN_ORDER_ATT = new AttributeConstructor(new LiteralExpression("inOrder"), new LiteralExpression("true"));
    private ParseableQuery[] clauses;
    private int slop;
    private boolean inOrder;

    public SpanNearPQuery(int slop, boolean inOrder, ParseableQuery ... clauses) {
        if (slop == 0 && inOrder) {
            this.clauses = mergeSubClauses(clauses);
        } else {
            this.clauses = clauses;
        }
        this.slop= slop;
        this.inOrder= inOrder;
    }

    // optimize? by simplifying nested queries
    private ParseableQuery[] mergeSubClauses(ParseableQuery [] nested) {
        ArrayList<ParseableQuery> subclauses = new ArrayList<ParseableQuery>();
        for (ParseableQuery clause : nested) {
            if (clause instanceof SpanNearPQuery) {
                SpanNearPQuery subquery = (SpanNearPQuery) clause;
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

    @Override
    public String toQueryString(String field, IndexConfiguration config) {
        String markerName = inOrder ? "lux_within:" : "lux_near:";
        StringBuilder buf = new StringBuilder();
        buf.append("(+").append(markerName).append(slop);
        for (ParseableQuery clause : clauses) {
            buf.append(' ').append (clause.toQueryString(field, config));
        }
        buf.append(')');
        return buf.toString();
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
