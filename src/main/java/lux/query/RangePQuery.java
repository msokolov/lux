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

	public enum Type { STRING(false), INT, LONG, FLOAT, DOUBLE; 
		public boolean isNumeric;
		Type (boolean numeric) { isNumeric = numeric; }
		Type () { isNumeric = true; }
	};
	
    public static final LiteralExpression FIELD_ATTR_NAME = new LiteralExpression("fieldName");
    private static final LiteralExpression TYPE_ATTR_NAME = new LiteralExpression("type");
    private static final LiteralExpression LOWER_TERM_ATTR_NAME = new LiteralExpression("lowerTerm");
    private static final LiteralExpression UPPER_TERM_ATTR_NAME = new LiteralExpression("upperTerm");
    private static final LiteralExpression INCLUDE_LOWER_ATTR_NAME = new LiteralExpression("includeLower");
    private static final LiteralExpression INCLUDE_UPPER_ATTR_NAME = new LiteralExpression("includeUpper");


    public static final QName TERM_RANGE_QUERY_QNAME = new QName("TermRangeQuery");
    public static final QName NUMERIC_RANGE_QUERY_QNAME = new QName("NumericRangeQuery");

    private String fieldName;
    private String lowerTerm;
    private String upperTerm;
    private boolean includeLower;
    private boolean includeUpper;
    private Type type;
        
    public RangePQuery(String fieldName, Type type, String lowerTerm, String upperTerm, boolean includeLower, boolean includeUpper) {
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

    public boolean getincludeUpper() {
        return includeUpper;
    }

    public Type getType() {
        return type;
    }

    @Override
    public ElementConstructor toXmlNode(String field, IndexConfiguration config) {
        ArrayList<AttributeConstructor> atts = new ArrayList<AttributeConstructor>();
        atts.add (new AttributeConstructor(FIELD_ATTR_NAME, new LiteralExpression (fieldName)));
        if (lowerTerm != null) {
            atts.add (new AttributeConstructor(LOWER_TERM_ATTR_NAME, new LiteralExpression (lowerTerm)));
        }
        if (upperTerm != null) {
            atts.add (new AttributeConstructor(UPPER_TERM_ATTR_NAME, new LiteralExpression (upperTerm)));
        }
        atts.add (new AttributeConstructor(INCLUDE_LOWER_ATTR_NAME, new LiteralExpression (Boolean.toString(includeLower))));
        atts.add (new AttributeConstructor(INCLUDE_UPPER_ATTR_NAME, new LiteralExpression (Boolean.toString(includeUpper))));
		if (! type.isNumeric) {
            return new ElementConstructor
                    (TERM_RANGE_QUERY_QNAME, LiteralExpression.EMPTY, atts.toArray(new AttributeConstructor[atts.size()]));
        }
        // TODO: precisionStep
        atts.add (new AttributeConstructor(TYPE_ATTR_NAME, new LiteralExpression (type.toString())));
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
    
    /**
     * compute the intersection of this query with the other, and store the result in this query 
     * @param other another RangeQuery 
     * @return whether the two queries could be merged, which they can if they share the same fieldName and type
     */
    public boolean intersect (RangePQuery other) {
    	if (! (other.fieldName.equals(fieldName) && other.type.equals(type))) {
    		return false;
    	}
		if (other.lowerTerm != null) {
			if (lowerTerm == null) {
    			lowerTerm = other.lowerTerm;
    			includeLower = other.includeLower;
			} else {
				int cmp = compare(lowerTerm, other.lowerTerm);
				if (cmp == 0) {
					includeLower = includeLower && other.includeLower;
				} else if (cmp < 0) {
	    			lowerTerm = other.lowerTerm;
					includeLower = other.includeLower;
				}
			}
    	}
		if (other.upperTerm != null) {
			if (upperTerm == null) {
    			upperTerm = other.upperTerm;
    			includeUpper = other.includeUpper;
			} else {
				int cmp = compare(upperTerm, other.upperTerm);
				if (cmp == 0) {
					includeUpper = includeUpper && other.includeUpper;
				} else if (cmp > 0) {
	    			upperTerm = other.upperTerm;
					includeUpper = other.includeUpper;
				}
			}
    	}
    	return true;
    }
    
    @Override
    public boolean equals (Object other) {
    	if (other == null) {
    		return false;
    	}
    	if (! (other instanceof RangePQuery)) {
    		return false;
    	}
    	RangePQuery oq = (RangePQuery) other;
    	if (includeLower != oq.includeLower || includeUpper != oq.includeUpper) {
    		return false;
    	}
    	if ((lowerTerm == null) != (oq.lowerTerm == null) || (upperTerm == null) != (oq.upperTerm == null)) {
    		return false;
    	}
    	return ((lowerTerm == null || lowerTerm.equals(oq.lowerTerm)) && ((upperTerm == null) || upperTerm.equals(oq.upperTerm)));
    }
    
    private int compare (String t1, String t2) {
    	if (type.isNumeric) {
    		return Double.valueOf(t1).compareTo(Double.valueOf(t2));
    	} else {
    		return t1.compareTo(t2);
    	}
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
