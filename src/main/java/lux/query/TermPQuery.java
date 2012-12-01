package lux.query;

import lux.index.IndexConfiguration;
import lux.query.parser.LuxQueryParser;
import lux.xml.QName;
import lux.xpath.LiteralExpression;
import lux.xquery.AttributeConstructor;
import lux.xquery.ElementConstructor;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.index.Term;

/**
 * Parseable analogue of TermQuery.
 */
public class TermPQuery extends ParseableQuery {

    private static final LiteralExpression BOOST_ATTR_NAME = new LiteralExpression("boost");

    private static final LiteralExpression FIELD_ATTR_NAME = new LiteralExpression("fieldName");

    private static final QName TERM_QUERY_QNAME = new QName("TermQuery");

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
        return toXmlNode(field, TERM_QUERY_QNAME);
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
    
    @Override
    public String toQueryString (String field, IndexConfiguration config) {
        
        StringBuilder buf = new StringBuilder ();

        if (StringUtils.isBlank(term.field())) {
            buf.append(field);
        } else {
            buf.append(term.field());
        }
        buf.append (':');
        
        buf.append (LuxQueryParser.escapeQParser(term.text()));
        
        if (boost != 1.0f) {
            buf.append('^').append(Float.toString(boost));
        }
        
        return buf.toString();
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
