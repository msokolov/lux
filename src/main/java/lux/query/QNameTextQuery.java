package lux.query;

import lux.xpath.LiteralExpression;
import lux.xpath.QName;
import lux.xquery.AttributeConstructor;
import lux.xquery.ElementConstructor;

import org.apache.lucene.index.Term;

/**
 * A parseable query that generates a QNameTextQuery.
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
