package lux.query;

import lux.xpath.LiteralExpression;
import lux.xpath.QName;
import lux.xquery.AttributeConstructor;
import lux.xquery.ElementConstructor;

import org.apache.lucene.index.Term;
import org.apache.lucene.util.ToStringUtils;

/**
 * A parseable query that generates a QNameTextQuery.
 * TODO: a surround qparser version 
 */
public class QNameTextQuery extends ParseableQuery {

    private static final LiteralExpression BOOST_ATTR_NAME = new LiteralExpression("boost");

    private static final LiteralExpression FIELD_ATTR_NAME = new LiteralExpression("fieldName");

    private static final LiteralExpression QNAME_ATTR_NAME = new LiteralExpression("qName");

    private static final QName QUERY_QNAME = new QName("QNameTextQuery");

    private final Term term;
    
    private final float boost;
    
    private final String qName;
    
    public QNameTextQuery(Term t, String qName, float boost) {
        this.term = t;
        this.boost = boost;
        this.qName = qName;
    }
    
    public QNameTextQuery(Term t, String qName) {
        this (t, qName, 1.0f);
    }
    
    public QNameTextQuery(Term t) {
        this (t, null, 1.0f);
    }

    public String toXmlString (String field) {
        StringBuilder buffer = new StringBuilder("<QNamePhraseQuery");
        appendContents(field, buffer);
        buffer.append("</QNamePhraseQuery>");
        return buffer.toString();
    }

    protected void appendContents(String field, StringBuilder buffer) {
        if (term.field() != null && !term.field().equals(field)) {
            buffer.append (" fieldName=");
            LiteralExpression.quoteString(term.field(), buffer);
        }
        if (boost != 1) {
            buffer.append (" boost=\"").append(boost).append("\"");
        }
        if (qName != null) {
            buffer.append (" qName=");
            LiteralExpression.quoteString(qName, buffer);
        }
        buffer.append(">").append(term.text());
    }
    
    public String toString (String field) {
        return QNameTextQuery.toString (field, qName, term, boost);
    }
    
    /**
     * This generates a string representation of the query that can be parsed by the 
     * {@link LuxQueryParser}, an extended version of the standard Lucene query parser, that includes
     * support for field prefixes including node names (QNames). 
     * 
     * @param field the default (<i>ie</i> in-scope) Lucene field name: ignored; for these queries, the field
     * name is determined by qName: {@link XmlTextField#getName} if qName is empty and {@link NodeTextField#getName} otherwise,
     * and this determination is made by the query parser, so we don't embed the field name here.
     * @param qName the name of the node (element or attribute) to search, or empty to search all text
     * @param term the term to search for
     * @param boost the relative weight of this query, used in relevance scoring
     * @return a parseable string representation of the query
     */
    public static String toString (String field, String qName, Term term, float boost) {

        StringBuilder buffer = new StringBuilder();
        /*
        if (!term.field().equals(field) && !term.field().isEmpty()) {
          buffer.append(term.field());
          buffer.append(":");
        }
        */
        if (qName != null) {
            buffer.append("node<").append(qName).append(':');
        } else {
            buffer.append("node<").append(':');            
        }
        // quote the term text
        buffer.append ('"');
        String value = term.text();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': buffer.append("\\\""); break;
                case '\\': buffer.append("\\\\"); break;
                default: buffer.append(c); break;
            }
        }
        buffer.append ('"');
        buffer.append(ToStringUtils.boost(boost));
        return buffer.toString();
    }

    @Override
    public ElementConstructor toXmlNode(String field) {
        return toXmlNode(field, QUERY_QNAME);
    }
    
    protected ElementConstructor toXmlNode (String field, QName elementName) {
        AttributeConstructor fieldAtt=null;
        if (!term.field().equals(field) && !term.field().isEmpty()) {
            fieldAtt = new AttributeConstructor(FIELD_ATTR_NAME, new LiteralExpression (term.field()));
        }
        AttributeConstructor boostAtt=null;
        if (boost != 1.0f) {
            boostAtt = new AttributeConstructor(BOOST_ATTR_NAME, new LiteralExpression (boost));
        }
        AttributeConstructor qNameAtt=null;
        if (qName!= null) {
            qNameAtt = new AttributeConstructor(QNAME_ATTR_NAME, new LiteralExpression (qName));
        }
        return new ElementConstructor
                (elementName, new LiteralExpression(term.text()), fieldAtt, qNameAtt, boostAtt);
    }

    public Term getTerm() {
        return term;
    }

    public float getBoost() {
        return boost;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
