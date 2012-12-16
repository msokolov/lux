package lux;

import org.junit.BeforeClass;
import org.junit.Test;

public class SearchTestNS extends BaseSearchTest {
    
    @BeforeClass
    public static void setup() throws Exception {
        setup ("lux/reader-test-ns.xml", "lux/reader-test.xml");
    }
    
    @Test
    public void testSearchNS () throws Exception {
        // Ideally, search string could use prefixes declared in surrounding context
        //  assertSearch ("1", "declare namespace x='http://lux.net{test}'; count(lux:search('<x\\:title:test'))", 1, 1);
        
        // namespace may be supplied explicitly
        assertSearch ("1", "count(lux:search('<title\\{http://lux.net\\{test\\}\\}:test'))", 1, 1);
        // no namespace
        assertSearch ("1", "count(lux:search('<title:test'))", 1, 1);
        // wildcarded namespace
        assertSearch ("1", "count(lux:search('<*:test'))", 2, 2);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
