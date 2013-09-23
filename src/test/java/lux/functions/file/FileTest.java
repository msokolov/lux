package lux.functions.file;

import static org.junit.Assert.assertEquals;
import lux.Evaluator;
import lux.IndexTestSupport;
import lux.XdmResultSet;

import net.sf.saxon.s9api.XdmValue;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test the implementation of lux:search() as a URI "resolved" by fn:collection()
 */
public class FileTest {
    
    private static IndexTestSupport indexTestSupport;
    private static Evaluator eval;
    
    @BeforeClass
    public static void setup() throws Exception {
        indexTestSupport = new IndexTestSupport("lux/reader-test.xml");
        eval = indexTestSupport.makeEvaluator();
    }
    
    @Test
    public void testIsDir() throws Exception {
        XdmResultSet result = eval.evaluate ("declare namespace file='http://expath.org/ns/file';" +
                                             "(file:is-dir('.'), file:is-dir('xxx'), file:is-dir('./src/test/resources/conf/schema.xml'))");
        assertEquals ("(true(), false(), false())", result.getXdmValue().getUnderlyingValue().toString());
    }

    @Test
    public void testExists() throws Exception {
        XdmResultSet result = eval.evaluate ("declare namespace file='http://expath.org/ns/file';" +
                                             "(file:exists('.'), file:exists('xxx'), file:exists('./src/test/resources/conf/schema.xml'))");
        assertEquals ("(true(), false(), true())", result.getXdmValue().getUnderlyingValue().toString());
    }

    @Test
    public void testListFiles() throws Exception {
        XdmResultSet result = eval.evaluate ("declare namespace file='http://expath.org/ns/file';" +
                                             "for $file in file:list('./src/test/resources/conf') order by $file return $file");
        XdmValue value = result.getXdmValue();
        assertEquals ("schema.xml", value.itemAt(0).getStringValue());
        assertEquals ("solrconfig.xml", value.itemAt(1).getStringValue());
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
