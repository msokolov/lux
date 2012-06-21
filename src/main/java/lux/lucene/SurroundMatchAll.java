package lux.lucene;


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
    
    public String toXml(String field) {
        return "<SpanTermQuery fieldName=\"" + field + "\">{}</SpanTermQuery>";
    }
    
    public String toString(String field) {
        return "{}";
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
