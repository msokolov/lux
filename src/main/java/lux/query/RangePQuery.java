package lux.query;

import java.util.ArrayList;

import lux.index.IndexConfiguration;
import lux.query.parser.LuxQueryParser;
import lux.xml.QName;
import lux.xpath.LiteralExpression;
import lux.xquery.AttributeConstructor;
import lux.xquery.ElementConstructor;

/**
 * Parseable analogue of TermRangeQuery and NumericRangeQuery.
 */
public class RangePQuery extends ParseableQuery {

    public static final LiteralExpression FIELD_ATTR_NAME = new LiteralExpression("fieldName");
    private static final LiteralExpression TYPE_ATTR_NAME = new LiteralExpression("type");
    private static final LiteralExpression LOWER_TERM_ATTR_NAME = new LiteralExpression("lowerTerm");
    private static final LiteralExpression UPPER_TERM_ATTR_NAME = new LiteralExpression("upperTerm");
    private static final LiteralExpression INCLUDE_LOWER_ATTR_NAME = new LiteralExpression("includeLower");
    private static final LiteralExpression INCLUDE_UPPER_ATTR_NAME = new LiteralExpression("includeUpper");


    public static final QName TERM_RANGE_QUERY_QNAME = new QName("TermRangeQuery");
    public static final QName NUMERIC_RANGE_QUERY_QNAME = new QName("NumericRangeQuery");

    private final String fieldName;
    private final String lowerTerm;
    private final String upperTerm;
    private final boolean includeLower;
    private final boolean includeUpper;
    private final String type;
        
    public RangePQuery(String fieldName, String type, String lowerTerm, String upperTerm, boolean includeLower, boolean includeUpper) {
        this.fieldName = fieldName;
        this.lowerTerm = lowerTerm;
        this.upperTerm = upperTerm;
        this.includeLower = includeLower;
        this.includeUpper = includeUpper;
        // type must be one of: string, int, long, float, double.  We leave the checking up to Lucene's parser, though, so in theory this coiuld be extended.
        this.type = type;
    }
    
    public String getFieldName() {
        return fieldName;
    }

    public String getLowerTerm() {
        return lowerTerm;
    }

    public String getUpperTerm() {
        return upperTerm;
    }

    public boolean getIncludeLower() {
        return includeLower;
    }

    public boolean getIncludeUpper() {
        return includeUpper;
    }

    public String getType() {
        return type;
    }

    @Override
    public ElementConstructor toXmlNode(String field, IndexConfiguration config) {
        ArrayList<AttributeConstructor> atts = new ArrayList<AttributeConstructor>();
        atts.add (new AttributeConstructor(FIELD_ATTR_NAME, new LiteralExpression (fieldName)));
        boolean isNumeric = ! "string".equals(type);
        if (lowerTerm != null) {
            atts.add (new AttributeConstructor(LOWER_TERM_ATTR_NAME, new LiteralExpression (lowerTerm)));
        }
        if (upperTerm != null) {
            atts.add (new AttributeConstructor(UPPER_TERM_ATTR_NAME, new LiteralExpression (upperTerm)));
        }
        atts.add (new AttributeConstructor(INCLUDE_LOWER_ATTR_NAME, new LiteralExpression (Boolean.toString(includeLower))));
        atts.add (new AttributeConstructor(INCLUDE_UPPER_ATTR_NAME, new LiteralExpression (Boolean.toString(includeUpper))));
		if (! isNumeric) {
            return new ElementConstructor
                    (TERM_RANGE_QUERY_QNAME, LiteralExpression.EMPTY, atts.toArray(new AttributeConstructor[atts.size()]));
        }
        // TODO: precisionStep
        atts.add (new AttributeConstructor(TYPE_ATTR_NAME, new LiteralExpression (type)));
        return new ElementConstructor
                (NUMERIC_RANGE_QUERY_QNAME, LiteralExpression.EMPTY, atts.toArray(new AttributeConstructor[atts.size()]));
    }
    
    @Override
    public String toQueryString (String field, IndexConfiguration config) {
        StringBuilder buf = new StringBuilder ();
        buf.append(fieldName).append (':')
           .append (includeLower ? '[' : '{')
           .append (lowerTerm == null ? '*' : LuxQueryParser.escapeQParser(lowerTerm))
           .append (" TO ")
           .append (upperTerm == null ? '*' : LuxQueryParser.escapeQParser(upperTerm))
           .append (includeUpper ? ']' : '}');
        return buf.toString();
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
