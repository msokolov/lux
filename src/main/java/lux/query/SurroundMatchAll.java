package lux.query;

import lux.index.field.XmlField;


/**
 * This query exists only to serve as a placeholder in an intermediate query compilation
 * phase.  It prints out a "match all" in surround query parser language;
 * it doesn't actually query anything.
 */
public class SurroundMatchAll extends ParseableQuery {
    
    private static final SurroundMatchAll INSTANCE = new SurroundMatchAll();
    
    public static final SurroundMatchAll getInstance () {
        return INSTANCE;
    }
    
    public String toXmlString(String field) {
        if (field.equals(XmlField.PATH.getName())) {
            return "<SpanTerm>{}</SpanTerm>";
        }
        return "<SpanTerm fieldName=\"" + XmlField.PATH.getName() + "\">{}</SpanTerm>";
    }
    
    public String toString(String field) {
        return "{}";
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
