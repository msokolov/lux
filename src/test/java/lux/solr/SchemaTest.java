package lux.solr;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.solr.common.SolrInputDocument;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/** Tests for configurable analysis chain */
public class SchemaTest extends BaseSolrTest {

    @BeforeClass
    public static void setup() throws Exception {
        // inhibit the startup of a default core by our superclass
    }
    
    @AfterClass
    public static void tearDown() throws Exception {
        // inhibit the class-level tearDown by our superclass; do it after each test:
    }
    
    @After
    public void myTearDown () throws Exception {
        BaseSolrTest.tearDown();
    }
    
    @Test
    public void testConfigureXmlAnalyzer () throws Exception {
        // schema alters the text analysis used for lux_text, lux_elt_text and lux_att_text as well
        BaseSolrTest.setup("solr", "core2");
        Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument> ();
        addSolrDoc ("test1", "<doc><title id='1'>This is a test</title><test>balloons</test>comma,separated</doc>", docs,
                "uri", "xml");
        solr.add (docs);
        solr.commit();
        // lux_text has case-folding, whitespace tokenization, and stemming:
        assertSolrQueryCount (1, "balloon");
        assertQueryCount (1, 1, "document", "doc", "lux:search('balloon')");
        assertSolrQueryCount (1, "balloons");
        assertQueryCount (1, 1, "document", "doc", "lux:search('balloons')");
        // check that query is analyzed as well
        assertSolrQueryCount (1, "tests");
        assertQueryCount (1, 1, "document", "doc", "lux:search('tests')");
        assertSolrQueryCount (0, "comma");
        assertQueryCount (0, 0, "document", "", "lux:search('comma')");
        assertSolrQueryCount (1, "comma,separated");
        assertQueryCount (1, 1, "document", "doc", "lux:search('comma,separated')");
        assertSolrQueryCount (1, "this");
        assertQueryCount (1, 1, "document", "doc", "lux:search('this')");
        assertSolrQueryCount (1, "This");
        assertQueryCount (1, 1, "document", "doc", "lux:search('This')");

        // schema includes a copyField from lux_text -> lux_text_unstemmed, which has no stemming 
        assertSolrQueryCount (1, "lux_text_unstemmed:balloons");
        assertQueryCount (1, 1, "document", "doc", "lux:search('lux_text_unstemmed:balloons')");
        assertSolrQueryCount (0, "lux_text_unstemmed:balloon");
        assertQueryCount (0, 0, "document", "", "lux:search('lux_text_unstemmed:balloon')");

        // schema includes a copyField from lux_text -> lux_text_case, which has is case-sensitive 
        assertSolrQueryCount (0, "lux_text_case:this");
        assertQueryCount (0, 0, "document", "", "lux:search('lux_text_case:this')");
        assertSolrQueryCount (1, "lux_text_case:This");
        assertQueryCount (1, 1, "document", "doc", "lux:search('lux_text_case:This')");
        
        // test that stemming and case-folding have been applied to the element text index as well
        assertSolrQueryCount (1, "lux_elt_text:test\\:balloon");
        assertQueryCount (1, 1, "document", "doc", "lux:search('<test:balloon')");
        // This doesn't work because stemming gets applied to the 'test:balloons'
        // but this isn't an issue if we just say that the supported thing is lux:search('<test:balloons')
        //assertQueryCount (1, "lux_elt_text:doc\\:balloons");
        assertQueryCount (1, 1, "document", "doc", "lux:search('<test:balloons')");
        //assertQueryCount (1, "lux_elt_text:doc\\:tests");
        assertQueryCount (1, 1, "document", "doc", "lux:search('<title:tests')");
        assertSolrQueryCount (0, "lux_elt_text:doc\\:comma");
        assertQueryCount (0, 0, "document", "", "lux:search('<doc:comma')");
        assertSolrQueryCount (1, "lux_elt_text:doc\\:comma,separated");
        assertQueryCount (1, 1, "document", "doc", "lux:search('<doc:comma,separated')");
        assertSolrQueryCount (1, "lux_elt_text:title\\:this");
        assertQueryCount (1, 1, "document", "doc", "lux:search('<title:this')");
        assertSolrQueryCount (1, "lux_elt_text:title\\:This");
        assertQueryCount (1, 1, "document", "doc", "lux:search('<title:This')");
    }

    @Test
    public void testDefaultXmlAnalyzer () throws Exception {
        // the default analyzer is based on StandardAnalyzer
        BaseSolrTest.setup("solr", "collection1");
        Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument> ();
        addSolrDoc ("test1", "<doc><title id='1'>This is a test</title><test>balloons</test>comma,separated</doc>", docs);
        solr.add (docs);
        solr.commit();
        // lux_text uses the (Lux) default analyzer which has case-folding, standard tokenization, and no stemming:
        assertSolrQueryCount (0, "balloon");
        assertQueryCount (0, 0, "", "", "lux:search('balloon')");
        assertSolrQueryCount (1, "lux_text:balloons");
        assertQueryCount (1, 1, "document", "doc", "lux:search('balloons')");
        assertSolrQueryCount (1, "lux_text:comma,separated");
        assertQueryCount (1, 1, "document", "doc", "lux:search('comma,separated')");
        assertSolrQueryCount (1, "lux_text:comma");
        assertQueryCount (1, 1, "document", "doc", "lux:search('comma')");
        assertSolrQueryCount (1, "lux_text:this");
        assertQueryCount (1, 1, "document", "doc", "lux:search('this')");
        assertSolrQueryCount (1, "lux_text:This");
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
