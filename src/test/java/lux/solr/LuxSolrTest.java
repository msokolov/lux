package lux.solr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import lux.index.field.XmlField;

import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.CoreContainer;
import org.junit.BeforeClass;
import org.junit.Test;

public class LuxSolrTest {
    
    private static SolrServer solr;
    
    private final String SOLR_QUERY_TYPE = "/xquery";
    
    @BeforeClass public static void setup () throws Exception {
        System.setProperty("solr.solr.home", "solr");
        CoreContainer.Initializer initializer = new CoreContainer.Initializer();
        CoreContainer coreContainer = initializer.initialize();
        solr = new EmbeddedSolrServer(coreContainer, "");
        solr.deleteByQuery("*:*");
        Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument> ();
        addSolrDocFromFile("src/test/resources/conf/schema.xml", docs);
        addSolrDocFromFile("src/test/resources/conf/solrconfig.xml", docs);
        for (int i = 1; i <= 100; i++) {
            addSolrDoc ("test" + i, "<doc><test id='" + i + "'>" + i + "</test><test>cat</test></doc>", docs);
        }
        solr.add (docs);
        solr.commit();
    }
    
    @Test public void testIndex() throws Exception {
        // make sure the documents have the values we expect
        assertQueryCount (102, "*:*");
        // QNAME index is no longer part of the default setup created by LuxUpdateProcessor
        // TODO: expose index config in solrconfig.xml
        //assertQueryCount (1, XmlField.ELT_QNAME.getName() + ":config");
        //assertQueryCount (1, XmlField.ELT_QNAME.getName() + ":schema");
        assertQueryCount (1, XmlField.PATH.getName() + ":\"{} schema types fieldType\"");
        assertQueryCount (1, XmlField.PATH.getName() + ":schema");
        assertQueryCount (102, XmlField.PATH.getName() + ":\"{}\"");
        assertQueryCount (1, XmlField.PATH.getName() + ":\"{} config luceneMatchVersion\"");
        assertQueryCount (2, XmlField.XML_TEXT + ":true");
        assertQueryCount (2, XmlField.ELEMENT_TEXT + ":enableLazyFieldLoading\\:true");
        assertQueryCount (1, XmlField.ATTRIBUTE_TEXT + ":id\\:1");
        assertQueryCount (1, XmlField.ATTRIBUTE_TEXT + ":type\\:random");
    }
    
    @Test public void testXPathSearch() throws Exception {
        // test search using standard search query handler, custom query parser
        assertXPathSearchCount (1, 1, "element", "config", "//config");
        assertXPathSearchCount (34, 1, 50, "element", "abortOnConfigurationError", "/config/*");
    }
    
    @Test public void testAtomicResult () throws Exception {
        // This also tests lazy evaluation - like paging within xpath.  Because we only retrieve
        // the first value (in document order), we only need to retrieve one value.
        assertXPathSearchCount (1, 1, "xs:double", "1", "number((/doc/test)[1])");
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
        assertXPathSearchCount (10, 100, "element", "doc", "//doc[test[number(.) > 5]]");
    }
    
    @Test public void testDocFunction () throws Exception {
        assertXPathSearchCount (1, 0, "document", "doc", "doc('test50')");        
        assertXPathSearchCount (0, 0, "error", "document '/foo' not found", "doc('/foo')");  
    }
    
    @Test public void testCollectionFunction () throws Exception {
        assertXPathSearchCount (1, 1, "xs:string", "src/test/resources/conf/schema.xml", "collection()[1]/base-uri()");
        // TODO: optimize count(collection ())
        // TODO: return an integer
        assertXPathSearchCount (1, 102, "xs:string", "102", "count(collection())");  
    }
    
    @Test public void testQueryError () throws Exception {
        SolrQuery q = new SolrQuery("lux_elt_name_ms:config");
        q.setParam("qt", SOLR_QUERY_TYPE);
        q.setParam("defType", "lucene");
        // TODO: return compile errors in the response; don't throw an exception
        // send an ordinary Lucene query to the XPathSearchComponent
        // throws a prefix undefined error
        try {
            solr.query (q);
            assertFalse (true);
        } catch (SolrServerException e) {
        }
    }
    
    @Test
    public void testSyntaxError () throws Exception {
        try {
            assertXPathSearchCount(0, 0, "", "", "hey bad boy");
            assertTrue ("expected ParseException to be thrown for syntax error", false);
        } catch (SolrServerException e) {
        }
    }
    
    protected void assertQueryCount (int count, String query) throws SolrServerException {
        SolrQuery q = new SolrQuery(query);
        QueryResponse rsp = solr.query (q);
        assertEquals (count, rsp.getResults().getNumFound());
    }
    
    protected void assertXPathSearchCount (int count, int docCount, String type, String value, String query) throws SolrServerException {
        assertXPathSearchCount(count, docCount, 10, type, value, query);
    }
    
    protected void assertXPathSearchCount (int count, int docCount, int maxResults, String type, String value, String query) throws SolrServerException {
        SolrQuery q = new SolrQuery(query);
        q.setQueryType(SOLR_QUERY_TYPE);
        q.setRows(maxResults);
        q.setStart(0);
        QueryResponse rsp = solr.query (q, METHOD.POST);
        long docMatches = rsp.getResults().getNumFound();
        assertEquals (docCount, docMatches);
        NamedList<?> results = (NamedList<?>) rsp.getResponse().get("xpath-results");
        assertEquals (count, results.size());
        if (type.equals("error")) {
            String error = (String) rsp.getResponse().get("xpath-error");
            assertEquals (value, error);
        } else {
            assertEquals (type, results.getName(0));
            String returnValue = results.getVal(0).toString();
            if (returnValue.startsWith ("<")) {
                // assume the returned value is an element - hack to avoid real parsing 
                assertEquals (value, returnValue.substring(1, returnValue.indexOf('>')));
            } else {
                assertEquals (value, returnValue);
            }
        }
    }

    private static void addSolrDocFromFile(String path, Collection<SolrInputDocument> docs) throws FileNotFoundException, IOException {
        SolrInputDocument doc = new SolrInputDocument(); 
        doc.addField (XmlField.URI.getName(), path);
        FileInputStream in = new FileInputStream (path);
        String buf = IOUtils.toString(in);
        doc.addField(XmlField.XML_STORE.getName(), buf);
        docs.add(doc);
    }
    
    private static void addSolrDoc(String uri, String text, Collection<SolrInputDocument> docs) throws FileNotFoundException, IOException {
        SolrInputDocument doc = new SolrInputDocument(); 
        doc.addField (XmlField.URI.getName(), uri);
        doc.addField(XmlField.XML_STORE.getName(), text);
        docs.add(doc);
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
