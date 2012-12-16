package lux;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class SearchTestNS extends BaseSearchTest {
    
    @BeforeClass
    public static void setup() throws Exception {
        setup ("lux/reader-test-ns.xml", "lux/reader-test.xml");
    }
    
    @Test
    public void testSearchNS () throws Exception {
        // namespace prefix may be supplied explicitly
        assertSearch ("2", "count(lux:search('<x\\:title:test'))", null, 2);
        // no namespace
        assertSearch ("2", "count(lux:search('<title:test'))", null, 2);
    }

    @Test
    public void testSearchNsUri () throws Exception {
        // namespace may be supplied explicitly
        assertSearch ("2", "count(lux:search('<title\\{http\\://lux.net\\{test\\}\\}:test'))", null, 2);
    }
    
    @Test @Ignore
    public void testSearchBoundNsPrefix() throws Exception {
        // Ideally, search string could use prefixes declared in surrounding context
        // This test should be run with a namespace-aware idnex
        assertSearch ("2", "declare namespace x='http://lux.net{test}'; count(lux:search('<x\\:title:test'))", null, 2);
    }
    
    @Test
    public void testSearchWildcardNamespace () throws Exception {
        // wildcarded namespace
        assertSearch ("4", "count(lux:search('<*:test'))", null, 4);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
