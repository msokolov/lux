package lux.query;

import java.util.ArrayList;

import lux.index.IndexConfiguration;
import lux.query.BooleanPQuery.Clause;
import lux.query.parser.LuxQueryParser;
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
    public ElementConstructor toXmlNode(String field, IndexConfiguration config) {
        if (config.isOption(IndexConfiguration.INDEX_EACH_PATH)) {
            String qs = toPathOccurrenceQueryString(field, config, false);
            // TODO: get the path field name from config
            AttributeConstructor fieldAtt = new AttributeConstructor(TermPQuery.FIELD_ATTR_NAME, new LiteralExpression ("lux_path"));
            // TODO: boost (and refactor)
            return new ElementConstructor (SpanTermPQuery.REGEXP_TERM_QNAME, new LiteralExpression(qs), fieldAtt);
        }
        AttributeConstructor inOrderAtt=null, slopAtt;
        if (inOrder) {
            inOrderAtt = IN_ORDER_ATT;
        }
        slopAtt = new AttributeConstructor(SLOP_ATT_NAME, new LiteralExpression(slop));
        if (clauses.length == 1) {
            return new ElementConstructor (SPAN_NEAR_QNAME, clauses[0].toXmlNode(field, config), inOrderAtt, slopAtt);
        }
        AbstractExpression[] clauseExprs = new AbstractExpression[clauses.length];
        int i = 0;
        for (ParseableQuery q : clauses) {
            clauseExprs [i++] = q.toXmlNode(field, config);
        }
        return new ElementConstructor (SPAN_NEAR_QNAME, new Sequence(clauseExprs), inOrderAtt, slopAtt);
    }

    @Override
    public String toQueryString(String field, IndexConfiguration config) {
        StringBuilder buf = new StringBuilder();
        if (config.isOption(IndexConfiguration.INDEX_EACH_PATH)) {
            buf.append(field).append(":/").append(toPathOccurrenceQueryString(field, config, true))
                .append('/');
        }
        String markerName = inOrder ? "lux_within:" : "lux_near:";
        buf.append("(+").append(markerName).append(slop);
        for (ParseableQuery clause : clauses) {
            buf.append(' ').append (clause.toQueryString(field, config));
        }
        buf.append(')');
        return buf.toString();
    }
    
    private String toPathOccurrenceQueryString (String field, IndexConfiguration config, boolean escape) {
        StringBuilder buf = new StringBuilder();
        // Escape slashes for the Lucene query parser since they are *part of the string to match*
        if (clauses.length > 0) {
            buf.append (toPathOccurrenceQueryString(clauses[clauses.length-1], field, config));
            for (int i = clauses.length-2; i >= 0; i--) {

                ParseableQuery clause = clauses[i];
                if (clause instanceof SpanMatchAll) {
                    if (slop > 0) {
                        buf.append(escape ? "(\\/.*)?" : "(/.*)?");
                    }
                    continue;
                }
                if (slop > 0) {
                    buf.append(escape ? "\\/.*" : "/.*");
                } else {
                    // don't translate a//b into b/*/a since that enforces an extra step
                    // TODO: index this differently so we can distinguish and not have names
                    // bleeding into wildcards.  Use two slashes to separate?
                    buf.append (escape ? "\\/" : "/");
                }
                buf.append (toPathOccurrenceQueryString(clause, field, config));
            }
            if (clauses[0] instanceof SpanTermPQuery) {
                // the path is not rooted, append a wildcard to get a prefix query
                buf.append(escape ? "(\\/.*)?" : "(/.*)?");
            }
        }
        return buf.toString();
    }
    
    // TODO: refactor this messiness
    private String toPathOccurrenceQueryString (ParseableQuery q, String field, IndexConfiguration config) {
        if (q instanceof SpanTermPQuery) {
            return (LuxQueryParser.escapeQParser(((SpanTermPQuery)q).getTerm().text()));
        } else if (q instanceof SpanNearPQuery) {
            return (((SpanNearPQuery) q).toPathOccurrenceQueryString(field, config, true));
        } else  if (q instanceof SpanBooleanPQuery) {
            // FIXME: you can also have a SpanOrQuery here: a/(b|c)/d
            // We'll implement using regex just as shown above
            StringBuilder buf = new StringBuilder ();
            Clause[] subClauses = ((SpanBooleanPQuery) q).getClauses();
            buf.append ('(');
            buf.append(toPathOccurrenceQueryString(subClauses[0].getQuery(), field, config));
            for (int i = 1; i < subClauses.length; i++) {
                buf.append ('|');
                buf.append(toPathOccurrenceQueryString(subClauses[i].getQuery(), field, config));
            }
            buf.append (')');
            return buf.toString();
        } else {
        	// TODO: SpanMatchAll ?
            throw new IllegalStateException(q.getClass().getName());
        }
    }

    @Override 
    public boolean isSpanCompatible() {
    	return true;
    }

    @Override
    public boolean equals(ParseableQuery other) {
        if (other instanceof SpanNearPQuery) {
            SpanNearPQuery oq = (SpanNearPQuery) other;
            if (! (slop == oq.slop && inOrder == oq.inOrder && clauses.length == oq.clauses.length)) {
                return false;
            }
            for (int i = 0; i < clauses.length; i++) {
                if (! clauses[i].equals(oq.clauses[i])) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
