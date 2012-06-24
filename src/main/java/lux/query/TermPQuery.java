package lux.query;

import lux.xpath.LiteralExpression;
import lux.xpath.QName;
import lux.xquery.AttributeConstructor;
import lux.xquery.ElementConstructor;

import org.apache.lucene.index.Term;
import org.apache.lucene.util.ToStringUtils;

/**
 * An analogue of TermQuery whose toString method produces an expression that can be parsed by
 * Lucene's standard query parser (and its surround query parser).
 *
 */
public class TermPQuery extends ParseableQuery {

    private static final LiteralExpression BOOST_ATTR_NAME = new LiteralExpression("boost");

    private static final LiteralExpression FIELD_ATTR_NAME = new LiteralExpression("fieldName");

    private static final QName TERMS_QUERY_QNAME = new QName("TermsQuery");

    private final Term term;
    
    private final float boost;
    
    public TermPQuery(Term t, float boost) {
        this.term = t;
        this.boost = boost;
    }
    
    public TermPQuery(Term t) {
        this (t, 1.0f);
    }
    
    public String toXmlString (String field) {
        StringBuilder buffer = new StringBuilder("<TermsQuery");
        appendContents(field, buffer);
        buffer.append("</TermsQuery>");
        return buffer.toString();
    }

    protected void appendContents(String field, StringBuilder buffer) {
        if (term.field() != null && !term.field().equals(field)) {
            buffer.append (" fieldName=\"").append (term.field()).append ("\"");
        }
        if (boost != 1) {
            buffer.append (" boost=\"").append(boost).append("\"");
        }
        buffer.append(">").append(term.text());
    }
    
    public String toString (String field) {
        return TermPQuery.toString (field, term, boost);
    }
    
    public static String toString (String field, Term term, float boost) {

        StringBuilder buffer = new StringBuilder();
        if (!term.field().equals(field) && !term.field().isEmpty()) {
          buffer.append(term.field());
          buffer.append(":");
        }
        // quote the term text in case it is an operator
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

    public Term getTerm() {
        return term;
    }

    public float getBoost() {
        return boost;
    }

    @Override
    public ElementConstructor toXmlNode(String field) {
        return toXmlNode(field, TERMS_QUERY_QNAME);
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
        return new ElementConstructor
                (elementName, new LiteralExpression(term.text()), fieldAtt, boostAtt);
        
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
