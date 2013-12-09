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
        addSolrDoc ("test1", "<doc><title id='1'>This is a test</title><test>balloons</test>comma,separated</doc>", docs);
        solr.add (docs);
        solr.commit();
        // lux_text has case-folding, whitespace tokenization, and stemming:
        assertQueryCount (1, "balloon");
        assertXPathSearchCount (1, 1, "document", "doc", "lux:search('balloon')");
        assertQueryCount (1, "balloons");
        assertXPathSearchCount (1, 1, "document", "doc", "lux:search('balloons')");
        // check that query is analyzed as well
        assertQueryCount (1, "tests");
        assertXPathSearchCount (1, 1, "document", "doc", "lux:search('tests')");
        assertQueryCount (0, "comma");
        assertXPathSearchCount (0, 0, "document", "", "lux:search('comma')");
        assertQueryCount (1, "comma,separated");
        assertXPathSearchCount (1, 1, "document", "doc", "lux:search('comma,separated')");
        assertQueryCount (1, "this");
        assertXPathSearchCount (1, 1, "document", "doc", "lux:search('this')");
        assertQueryCount (1, "This");
        assertXPathSearchCount (1, 1, "document", "doc", "lux:search('This')");

        // schema includes a copyField from lux_text -> lux_text_unstemmed, which has no stemming 
        assertQueryCount (1, "lux_text_unstemmed:balloons");
        assertXPathSearchCount (1, 1, "document", "doc", "lux:search('lux_text_unstemmed:balloons')");
        assertQueryCount (0, "lux_text_unstemmed:balloon");
        assertXPathSearchCount (0, 0, "document", "", "lux:search('lux_text_unstemmed:balloon')");

        // schema includes a copyField from lux_text -> lux_text_case, which has is case-sensitive 
        assertQueryCount (0, "lux_text_case:this");
        assertXPathSearchCount (0, 0, "document", "", "lux:search('lux_text_case:this')");
        assertQueryCount (1, "lux_text_case:This");
        assertXPathSearchCount (1, 1, "document", "doc", "lux:search('lux_text_case:This')");
        
        // test that stemming and case-folding have been applied to the element text index as well
        assertQueryCount (1, "lux_elt_text:test\\:balloon");
        assertXPathSearchCount (1, 1, "document", "doc", "lux:search('<test:balloon')");
        // This doesn't work because stemming gets applied to the 'test:balloons'
        // but this isn't an issue if we just say that the supported thing is lux:search('<test:balloons')
        //assertQueryCount (1, "lux_elt_text:doc\\:balloons");
        assertXPathSearchCount (1, 1, "document", "doc", "lux:search('<test:balloons')");
        //assertQueryCount (1, "lux_elt_text:doc\\:tests");
        assertXPathSearchCount (1, 1, "document", "doc", "lux:search('<title:tests')");
        assertQueryCount (0, "lux_elt_text:doc\\:comma");
        assertXPathSearchCount (0, 0, "document", "", "lux:search('<doc:comma')");
        assertQueryCount (1, "lux_elt_text:doc\\:comma,separated");
        assertXPathSearchCount (1, 1, "document", "doc", "lux:search('<doc:comma,separated')");
        assertQueryCount (1, "lux_elt_text:title\\:this");
        assertXPathSearchCount (1, 1, "document", "doc", "lux:search('<title:this')");
        assertQueryCount (1, "lux_elt_text:title\\:This");
        assertXPathSearchCount (1, 1, "document", "doc", "lux:search('<title:This')");
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
        assertQueryCount (0, "balloon");
        assertXPathSearchCount (0, 0, "", "", "lux:search('balloon')");
        assertQueryCount (1, "lux_text:balloons");
        assertXPathSearchCount (1, 1, "document", "doc", "lux:search('balloons')");
        assertQueryCount (1, "lux_text:comma,separated");
        assertXPathSearchCount (1, 1, "document", "doc", "lux:search('comma,separated')");
        assertQueryCount (1, "lux_text:comma");
        assertXPathSearchCount (1, 1, "document", "doc", "lux:search('comma')");
        assertQueryCount (1, "lux_text:this");
        assertXPathSearchCount (1, 1, "document", "doc", "lux:search('this')");
        assertQueryCount (1, "lux_text:This");
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
