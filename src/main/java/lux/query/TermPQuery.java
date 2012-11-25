package lux.query;

import lux.xml.QName;
import lux.xpath.LiteralExpression;
import lux.xquery.AttributeConstructor;
import lux.xquery.ElementConstructor;

import org.apache.lucene.index.Term;

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
        //if (!term.field().equals(field) && !term.field().isEmpty()) {
            fieldAtt = new AttributeConstructor(FIELD_ATTR_NAME, new LiteralExpression (term.field()));
        //}
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
