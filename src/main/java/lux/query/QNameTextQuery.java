package lux.query;

import lux.index.IndexConfiguration;
import lux.xml.QName;
import lux.xpath.LiteralExpression;
import lux.xquery.AttributeConstructor;
import lux.xquery.ElementConstructor;

import org.apache.commons.lang.StringUtils;
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

    /**
     * @throws IllegalStateException if a qName was provided, but the field is not one of the
     * known QName-based fields (lux_elt_text or lux_att_text)
     */
    @Override
    public String toQueryString (String field, IndexConfiguration config) {
        StringBuilder buf = new StringBuilder ();
        String tf = term.field();
        if (StringUtils.isBlank(qName)) {
            buf.append ('<').append(':').append(term.text());
        }
        else if (tf.equals(config.getFieldName(IndexConfiguration.ELEMENT_TEXT))) {
            buf.append ('<').append(qName).append(':').append(term.text());
        }
        else if (tf.equals(config.getFieldName(IndexConfiguration.ATTRIBUTE_TEXT))) {
            buf.append ("<@").append(qName).append(':').append(term.text());
        }
        else {
            throw new IllegalStateException ("QNameTextQuery has qName with unknown field: " + tf);
        }
        if (boost != 1.0f) {
            buf.append('^').append(boost);
        }
        return buf.toString();
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
