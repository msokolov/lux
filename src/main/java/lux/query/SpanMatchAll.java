package lux.query;

import lux.index.IndexConfiguration;
import lux.xml.QName;
import lux.xpath.LiteralExpression;
import lux.xquery.AttributeConstructor;
import lux.xquery.ElementConstructor;

/**
 * This query exists only to serve as a placeholder in an intermediate query compilation
 * phase.  It prints out a query designed to match all documents in surround query parser language; 
 * because there is no built-in match-all query in that dialect, it prints term query matching the start
 * of a path in the lux_path field, which always begins with a fixed token: {}.
 * 
 * TODO: determine how much efficiency is lost by using a SpanTerm query rather than a real MatchAllDocsQuery.
 */
public class SpanMatchAll extends ParseableQuery {

    private static final ElementConstructor INSTANCE_ELEMENT_CONSTRUCTOR = 
            new ElementConstructor (new QName("SpanTerm"), new LiteralExpression("{}"), 
                    new AttributeConstructor(new LiteralExpression ("fieldName"), new LiteralExpression ("lux_path")));
    private static final SpanMatchAll INSTANCE = new SpanMatchAll();
    
    public static final SpanMatchAll getInstance () {
        return INSTANCE;
    }    

    @Override
    public ElementConstructor toXmlNode(String field, IndexConfiguration config) {
        if (config.isOption(IndexConfiguration.INDEX_EACH_PATH)) {
            return MatchAllPQuery.INSTANCE_ELEMENT_CONSTRUCTOR;
        }
        return INSTANCE_ELEMENT_CONSTRUCTOR;
    }

    @Override
    public String toQueryString(String defaultField, IndexConfiguration config) {
    	return "*:*";
    }
    
    @Override
    public boolean isSpan() {
    	return true;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
