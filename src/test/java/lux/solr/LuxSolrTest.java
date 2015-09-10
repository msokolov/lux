package lux.solr;


import static org.junit.Assert.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.TimeZone;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.junit.BeforeClass;
import org.junit.Test;

public class LuxSolrTest extends BaseSolrTest {
    
    private static final String XML_TEXT = "lux_text";
    private static final String LUX_PATH = "lux_path";

    @BeforeClass
    public static void setup () throws Exception {
        BaseSolrTest.setup("solr", "core2"); // use core2 since its schema has the element visibility configuration
        Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument> ();
        addSolrDocFromFile("src/test/resources/conf/schema.xml", docs, "uri", "xml");
        addSolrDocFromFile("src/test/resources/conf/solrconfig.xml", docs, "uri", "xml");
        for (int i = 1; i <= 100; i++) {
            addSolrDoc ("test" + i, "<doc><title id='" + i + "'>" + (101-i) + "</title><test>cat</test></doc>", docs, "uri", "xml");
        }
        solr.add (docs);
        solr.commit();
    }
    
    @Test public void testIndex() throws Exception {
        // make sure the documents have the values we expect
        assertSolrQueryCount (102, "*:*");
        // QNAME index is no longer part of the default setup created by LuxUpdateProcessor
        //assertQueryCount (1, XmlIndexer.ELT_QNAME.getName() + ":config");
        //assertQueryCount (1, XmlIndexer.ELT_QNAME.getName() + ":schema");
        assertSolrQueryCount (1, LUX_PATH + ":\"{} schema types fieldType\"");
        assertSolrQueryCount (1, LUX_PATH + ":schema");
        assertSolrQueryCount (102, LUX_PATH + ":\"{}\"");
        assertSolrQueryCount (1, LUX_PATH + ":\"{} config luceneMatchVersion\"");
        assertSolrQueryCount (2, XML_TEXT + ":true");
        assertQueryCount (2, 2, "xs:string", "schema", "lux:search('<enableLazyFieldLoading:true')/*/name()");
        // this fails due to lower-casing of the embedded tag
        // assertQueryCount (2, LUX_ELT_TEXT + ":enableLazyFieldLoading\\:true");
        assertQueryCount (1, 1, "xs:string", "doc", "lux:search('<@id:1')/*/name()");
        assertQueryCount (1, 1, "xs:string", "schema", "lux:search('<@type:random')/*/name()");
        // these fails due to tokenization of the tagged term
        // assertQueryCount (1, LUX_ATT_TEXT + ":id\\:1");
        // assertQueryCount (1, LUX_ATT_TEXT + ":type\\:random");
    }
    
    @Test public void testXPathSearch() throws Exception {
        // test search using standard search query handler, custom query parser
        assertQueryCount (1, 1, "element", "config", "//config");
        assertQueryCount (34, 1, 50, "element", "abortOnConfigurationError", "/config/*");
    }
    
    @Test public void testAtomicResult () throws Exception {
        // This also tests lazy evaluation - like paging within xpath.  Because we only retrieve
        // the first value (in document order), we only need to retrieve one value.
        assertQueryCount (1, 1, "xs:double", "100.0", "number((/doc/title)[1])");
    }
    
    @Test public void testLiteral () throws Exception {
        // no documents were retrieved, 1 result returned = 12
        assertQueryCount(1, 0, "xs:double", "12.0", "xs:double(12.0)");
    }
    
    @Test public void testFirstPage () throws Exception {
        // returns only the page including the first 10 results
        assertQueryCount (10, 10, "document", "doc", "(/)[doc]");
        
        assertQueryCount (10, 20, "element", "doc", "(//doc)[position() > 10]");
    }
    
    @Test public void testPaging () throws Exception {
        // make the searcher page past the first 10 documents to find 10 xpath matches
        assertQueryCount (10, 16, "element", "doc", "//doc[title[number(.) < 95]]");
    }
    
    /**
     * This test confirms that fields declared in solrconfig.xml/schema.xml are indexed
     * and made available for sorting, as well as exercising sorting optimization and the 
     * string sorting implementations
     * @throws Exception 
     */
    @Test public void testSorting () throws Exception {
        // should be 1, 10, 100, 11, 12, ..., 2, 21, 22, ...
        // which is docs 101, 92, 2, (since there are 2 docs with no title that are loaded first)
        assertQueryCount(1, 5, "xs:string", "1,10,100,11,12", "string-join(subsequence((for $doc in //doc order by $doc/lux:key('title') return $doc/title/string()),1,5),',')");
        assertQueryCount(1, 1, "xs:string", "1", "(for $doc in //doc order by $doc/lux:key('title') return $doc/title/string())[1]");
        assertQueryCount(1, 1, "xs:string", "99", "(for $doc in //doc order by $doc/lux:key('title') descending return $doc/title/string())[1]");
        assertQueryCount(1, 2, "xs:string", "10", "(for $doc in //doc order by $doc/lux:key('title') return $doc/title/string())[2]");
        // test providing the sort criteria directly to lux:search()
        assertQueryCount(1, 2, "xs:string", "10", "(for $doc in lux:search('<test:cat', 'title') return $doc/doc/title/string())[2]");
        // TODO: implement wildcard element query to test for existence of some element
        // assertXPathSearchCount(1, 2, "xs:string", "10", "lux:search('<doc:*', (), 'title')[2]");
    }
    
    @Test public void testDocFunction () throws Exception {
        assertQueryCount (1, 0, "document", "doc", "doc('test50')");
        assertQueryCount (0, 0, "error", "document not found: /foo\n", "doc('/foo')");
    }
    
    @Test public void testCollectionFunction () throws Exception {
        assertQueryCount (1, 1, "xs:anyURI", "lux:/src/test/resources/conf/schema.xml", "collection()[1]/base-uri()");
        assertQueryCount (1, 102, "xs:anyURI", "lux:/test100", "collection()[last()]/base-uri()");
        assertQueryCount (1, 102, "xs:integer", "102", "count(collection())");
    }
    
    @Test public void testQueryError () throws Exception {
        assertQueryError("Prefix lux_elt_name_ms has not been declared; Line#: 1; Column#: 22\n", "lux_elt_name_ms:config");
    }
    
    @Test
    public void testSyntaxError () throws Exception {
        assertQueryError("Unexpected token name \"bad\" beyond end of query; Line#: 1; Column#: 4\n", "hey bad boy");
    }
    
    @Test
    public void testCreateCore () throws Exception {
        SolrQuery q = new SolrQuery();
        q.setRequestHandler(coreContainer.getAdminPath());
        q.setParam ("action", "CREATE");
        q.setParam ("name", "core3");
        q.setParam ("instanceDir", "core3");
        solr.query(q);
        SolrServer core3 = new EmbeddedSolrServer(coreContainer, "core3");
        core3.deleteByQuery("*:*");
        core3.commit();
        assertSolrQueryCount (0, "*:*", core3);
        // main core still works
        assertQueryCount (1, 102, "xs:integer", "102", "count(collection())", solr);
        // new core working too
        assertQueryCount(1, 0, "xs:integer", "0", "count(collection())", core3);
    }
    
    @Test
    public void testAppServer () throws Exception {
        SolrQuery q = new SolrQuery();
        q.setRequestHandler("/testapp");
    	q.setParam("test-param", "test-value");
    	q.setParam("wt", "lux");
    	q.setParam("lux.contentType", "text/xml");
    	QueryResponse resp = solr.query(q);
    	assertEquals ("query was blank", resp.getResponse().get("xpath-error"));
    	q.setParam("lux.xquery", "lux/solr/echo.xqy");
    	resp = solr.query(q);
    	NamedList<?> xpathResults = (NamedList<?>) resp.getResponse().get("xpath-results"); 
    	assertEquals (
    			"<http><params>" +
    			"<param name=\"qt\"><value>/testapp</value></param>" +
    			"<param name=\"test-param\"><value>test-value</value></param>" +
    			"<param name=\"wt\"><value>lux</value></param></params><context-path/></http>", 
    			xpathResults.get("document").toString());
    	assertTrue(resp.getResults().isEmpty());
    }
    
    @Test
    public void testPrimitiveValues () throws Exception {
        assertQuery (true, "xs:boolean", "true()");
        assertQuery (false, "xs:boolean", "false()");
        
        assertQuery ("{local}name", "xs:QName", "fn:QName('local','l:name')");
        
        assertQuery (null, null, "()");
        assertQuery ("x", "xs:untypedAtomic", "xs:untypedAtomic('x')");
        
        assertQuery (1L, "xs:integer", "xs:integer(1)");
        assertQuery (1L, "xs:integer", "1");
        assertQuery (1L, "xs:int", "xs:int(1)");
        assertQuery (1.0, "xs:decimal", "1.0");
        assertQuery (1.0, "xs:double", "xs:double(1.0)");
        assertQuery (1.0f, "xs:float", "xs:float(1.0)");
        assertQuery ("1", "xs:string", "'1'");
        assertQuery ("1", "xs:string", "xs:anyURI('1')");
        
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(2013,3,6,0,0,0);
        cal.set(Calendar.MILLISECOND, 0);
        assertQuery (cal.getTime(), "xs:date", "xs:date('2013-04-06')");
        assertQuery (cal.getTime(), "xs:dateTime", "xs:dateTime('2013-04-06T00:00:00')");
        assertQuery ("00:00:00", "xs:time", "xs:time('00:00:00')");
        
        assertQuery ("0900", "xs:gYear", "xs:gYear('0900')");
        assertQuery ("--11", "xs:gMonth", "xs:gMonth('--11')");
        assertQuery ("2012-12", "xs:gYearMonth", "xs:gYearMonth('2012-12')");
        assertQuery ("---01", "xs:gDay", "xs:gDay('---01')");
        assertQuery ("--12-01", "xs:gMonthDay", "xs:gMonthDay('--12-01')");
        
    }

    @Test
    public void testMultiNodeConstruct () throws Exception {
        String xml = "document {comment { ' this is a test ' }, \n"
                + "processing-instruction test-pi { 'this is a test pi' },\n"
                + "element test { 'Hello, World' } }";
        String output = "<!-- this is a test --><?test-pi this is a test pi?><test>Hello, World</test>"; 
        assertQuery (output, "document", xml);
    }
    
    /*
     * Make sure we can index date-valued fields.  Test inserting via REST and via XQuery, retrieving
     * field values, and querying.
     */
    @Test
    public void testDateField () throws Exception {
        assertQuery ("ok", "(lux:insert('/test', <dfdoc modified='2000-01-01T01:02:03Z'>ok</dfdoc>), lux:commit(), 'ok')");
        assertQuery ("ok", "(lux:insert('/test2', <dfdoc modified='2000-01-01T02:03:04Z'>dokey</dfdoc>), lux:commit(), 'ok')");
        Date d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz").parse("2000-01-01T01:02:03GMT+00:00");
        assertSolrQuery(d, "modified_dt", "uri:\\/test");
        assertSolrQuery(d, "modified_tdt", "uri:\\/test");
        
        assertQuery ("ok", "/dfdoc[@modified='2000-01-01T01:02:03Z']/string()");
        assertQuery ("ok", "/dfdoc[@modified=xs:dateTime('2000-01-01T01:02:03Z')]/string()");
        
        // DateField
        assertQuery ("2000-01-01T01:02:03Z", "lux:key('modified_dt', doc('/test'))");
        assertQuery ("dokey", "(for $doc in /dfdoc order by $doc/lux:key('modified_dt') return $doc)[2]/string()");
        assertQuery ("dokey", "(for $doc in /dfdoc order by $doc/lux:key('modified_dt') descending return $doc)[1]/string()");
        
        // TrieDateField
        assertQuery ("2000-01-01T01:02:03Z", "lux:key('modified_tdt', doc('/test'))");
        assertQuery ("dokey", "(for $doc in /dfdoc order by $doc/lux:key('modified_tdt') return $doc)[2]/string()");
        assertQuery ("dokey", "(for $doc in /dfdoc order by $doc/lux:key('modified_tdt') descending return $doc)[1]/string()");
        
        // queries
        assertQuery ("ok", "lux:search('modified_dt:\"2000-01-01T01:02:03Z\"')/string()");
        // query parser can't handle date ranges
        // assertQuery ("ok", "lux:search('modified_dt:[\"2000-01-01T01:02:03Z\" TO *]')/string()");
        // lack of precision makes this return 2?
        // assertQuery ("ok", "lux:search('modified_tdt:\"2000-01-01T01:02:03Z\"')/string()");
        // assertQuery ("ok", "lux:search('modified_tdt:[\"2000-01-01T01:02:03Z\" TO *]')/string()");

        assertQuery ("ok", "lux:search('<@modified:\"2000-01-01T01:02:03Z\"')/string()");
        // assertQuery ("ok", "lux:search('<@modified:[\"2000-01-01T01:02:03Z\" TO *]')/string()");
        // bad date format
        try {
            assertQueryError("ok", "(lux:insert('/test', <doc modified='2000-01-01' />), lux:commit(), 'ok')");
            assertFalse ("no exception thrown", true);
        } catch (Exception ex) {
            assertTrue (ex.getMessage().contains("2000-01-01"));
        }
        
        assertQuery ("ok", "(lux:delete('/test'), lux:delete('/test2'), lux:commit(), 'ok')");
    }
    
    @Test
    public void testInsertRandomFields () throws Exception {
        // test inserting a document that doesn't have the Lux XML field
        SolrInputDocument doc = new SolrInputDocument(); 
        doc.addField ("uri", "/doc/string10");
        doc.addField ("string_s", "string");
        doc.addField("number_i", "10");
        try {
            solr.add(doc);
            solr.commit();
            assertQuery ("<binary xmlns=\"http://luxdb.net\"/>", "document", "doc('/doc/string10')");
        } finally {
            // now clean up
            solr.deleteById("/doc/string10");
            solr.commit();
        }
    }
    
    @Test
    public void testConfigElementVisibility () throws Exception {
        assertQuery ("ok", "(lux:insert('/test', <doc><div>ok <hidden>bad</hidden><i>go</i> <x>away</x></div> <x><i>often</i> enough</x></doc>), lux:commit(), 'ok')");
        // xml text field includes all text
        assertQueryCount (1, "lux:search('ok')/string()");
        assertQueryCount (1, "lux:search('go')/string()");
        // except text in hidden elements
        assertQueryCount (0, "lux:search('bad')/string()");
        
        // xml element text field includes transparent elements
        assertQueryCount (1, "lux:search('<i:go')/string()");
        // container element sees into opaque and transparent elements 
        assertQueryCount (1, "lux:search('<div:go')/string()");
        assertQueryCount (1, "lux:search('<div:away')/string()");
        // but not hidden elements
        assertQueryCount (0, "lux:search('<hidden:bad')/string()");
        // even as part of container elements
        assertQueryCount (0, "lux:search('<div:bad')/string()");
        // phrase wraps around hidden element
        assertQueryCount (1, "lux:search('ok go')/string()");
        
        // regular element does not "see" into opaque or container elements
        assertQueryCount (0, "lux:search('<doc:ok')/string()");
        assertQueryCount (0, "lux:search('<doc:often')/string()");
        assertQueryCount (0, "lux:search('<doc:enough')/string()");

        // but does see into itself, and transparent element
        assertQueryCount (1, "lux:search('<x:\"often enough\"')/string()");
        
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
