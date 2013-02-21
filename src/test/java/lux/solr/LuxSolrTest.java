package lux.solr;


import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.io.IOUtils;
import org.apache.solr.common.SolrInputDocument;
import org.junit.BeforeClass;
import org.junit.Test;

public class LuxSolrTest extends BaseSolrTest {
    
    private static final String LUX_XML = "lux_xml";
    private static final String URI = "lux_uri";
    private static final String XML_TEXT = "lux_text";
    private static final String LUX_PATH = "lux_path";
    private static final String LUX_ELT_TEXT = "lux_elt_text";
    private static final String LUX_ATT_TEXT = "lux_att_text";

    @BeforeClass
    public static void setup () throws Exception {
        BaseSolrTest.setup();
        Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument> ();
        addSolrDocFromFile("src/test/resources/conf/schema.xml", docs);
        addSolrDocFromFile("src/test/resources/conf/solrconfig.xml", docs);
        for (int i = 1; i <= 100; i++) {
            addSolrDoc ("test" + i, "<doc><title id='" + i + "'>" + (101-i) + "</title><test>cat</test></doc>", docs);
        }
        solr.add (docs);
        solr.commit();
    }
    
    @Test public void testIndex() throws Exception {
        // make sure the documents have the values we expect
        assertQueryCount (102, "*:*");
        // QNAME index is no longer part of the default setup created by LuxUpdateProcessor
        //assertQueryCount (1, XmlIndexer.ELT_QNAME.getName() + ":config");
        //assertQueryCount (1, XmlIndexer.ELT_QNAME.getName() + ":schema");
        assertQueryCount (1, LUX_PATH + ":\"{} schema types fieldType\"");
        assertQueryCount (1, LUX_PATH + ":schema");
        assertQueryCount (102, LUX_PATH + ":\"{}\"");
        assertQueryCount (1, LUX_PATH + ":\"{} config luceneMatchVersion\"");
        assertQueryCount (2, XML_TEXT + ":true");
        assertQueryCount (2, LUX_ELT_TEXT + ":enableLazyFieldLoading\\:true");
        assertQueryCount (1, LUX_ATT_TEXT + ":id\\:1");
        assertQueryCount (1, LUX_ATT_TEXT + ":type\\:random");
    }
    
    @Test public void testXPathSearch() throws Exception {
        // test search using standard search query handler, custom query parser
        assertXPathSearchCount (1, 1, "element", "config", "//config");
        assertXPathSearchCount (34, 1, 50, "element", "abortOnConfigurationError", "/config/*");
    }
    
    @Test public void testAtomicResult () throws Exception {
        // This also tests lazy evaluation - like paging within xpath.  Because we only retrieve
        // the first value (in document order), we only need to retrieve one value.
        assertXPathSearchCount (1, 1, "xs:double", "100", "number((/doc/title)[1])");
    }
    
    @Test public void testLiteral () throws Exception {
        // no documents were retrieved, 1 result returned = 12
        assertXPathSearchCount(1, 0, "xs:double", "12", "xs:double(12.0)");
    }
    
    @Test public void testFirstPage () throws Exception {
        // returns only the page including the first 10 results
        assertXPathSearchCount (10, 100, "document", "doc", "(/)[doc]");
        
        assertXPathSearchCount (10, 100, "element", "doc", "(//doc)[position() > 10]");
    }
    
    @Test public void testPaging () throws Exception {
        // make the searcher page past the first 10 documents to find 10 xpath matches
        assertXPathSearchCount (10, 100, "element", "doc", "//doc[title[number(.) < 95]]");
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
        assertXPathSearchCount(1, 5, "xs:string", "1,10,100,11,12", "string-join(subsequence((for $doc in //doc order by $doc/lux:field-values('title') return $doc/title/string()),1,5),',')");
        assertXPathSearchCount(1, 1, "xs:string", "1", "(for $doc in //doc order by $doc/lux:field-values('title') return $doc/title/string())[1]");
        assertXPathSearchCount(1, 1, "xs:string", "99", "(for $doc in //doc order by $doc/lux:field-values('title') descending return $doc/title/string())[1]");
        assertXPathSearchCount(1, 2, "xs:string", "10", "(for $doc in //doc order by $doc/lux:field-values('title') return $doc/title/string())[2]");
        // test providing the sort criteria directly to lux:search()
        assertXPathSearchCount(1, 2, "xs:string", "10", "(for $doc in lux:search('<test:cat', (), 'title') return $doc/doc/title/string())[2]");
        // TODO: implement wildcard element query to test for existence of some element
        // assertXPathSearchCount(1, 2, "xs:string", "10", "lux:search('<doc:*', (), 'title')[2]");
    }
    
    @Test public void testDocFunction () throws Exception {
        assertXPathSearchCount (1, 0, "document", "doc", "doc('test50')");
        // TODO: eliminate the repetition of the error:
        assertXPathSearchCount (0, 0, "error", "document not found: /foo\ndocument not found: /foo\n", "doc('/foo')");  
    }
    
    @Test public void testCollectionFunction () throws Exception {
        assertXPathSearchCount (1, 1, "xs:string", "lux:/src/test/resources/conf/schema.xml", "collection()[1]/base-uri()");
        // TODO: optimize count(collection ())
        // TODO: return an integer
        assertXPathSearchCount (1, 102, "xs:string", "102", "count(collection())");  
    }
    
    @Test public void testQueryError () throws Exception {
        assertXPathSearchError("Prefix lux_elt_name_ms has not been declared; Line#: 1; Column#: 22\n", "lux_elt_name_ms:config");
    }
    
    @Test
    public void testSyntaxError () throws Exception {
        assertXPathSearchError("Unexpected token name \"bad\" beyond end of query; Line#: 1; Column#: 4\n", "hey bad boy");
    }
    
    static void addSolrDocFromFile(String path, Collection<SolrInputDocument> docs) throws FileNotFoundException, IOException {
        SolrInputDocument doc = new SolrInputDocument(); 
        doc.addField (URI, path);
        FileInputStream in = new FileInputStream (path);
        String buf = IOUtils.toString(in);
        doc.addField(LUX_XML, buf);
        docs.add(doc);
    }
    
    static void addSolrDoc(String uri, String text, Collection<SolrInputDocument> docs) throws FileNotFoundException, IOException {
        SolrInputDocument doc = new SolrInputDocument(); 
        doc.addField (URI, uri);
        doc.addField(LUX_XML, text);
        docs.add(doc);
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
