package lux.solr;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.core.SolrCore;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public abstract class BaseSolrTest {

    protected static SolrServer solr;
    protected static CoreContainer coreContainer;
    protected static SolrCore solrCore;
    
    public final String SOLR_QUERY_TYPE = "/xquery";
    private static final String LUX_XML = "lux_xml";
    private static final String URI = "lux_uri";

    @BeforeClass
    public static void setup() throws Exception {
       setup ("solr");
    }
    
    protected static void setup(String solrHome) throws Exception {
        System.setProperty("solr.solr.home", solrHome);
        File f = new File("solr/collection1/data/tlog");
        if (f.exists()) {
            FileUtils.cleanDirectory (f);
        }
        f = new File("solr/collection1/data/index");
        if (f.exists ()) {
            FileUtils.cleanDirectory (new File("solr/collection1/data/index"));
        }
        coreContainer = new CoreContainer (solrHome);
        coreContainer.load();
        String defaultCoreName = coreContainer.getDefaultCoreName();
        solr = new EmbeddedSolrServer(coreContainer, defaultCoreName);
        solrCore = coreContainer.getCore(defaultCoreName);
        try {
            solr.deleteByQuery("*:*");
            solr.commit();
        } catch (SolrException e){
            // might get "no such core" in the multi-core config
        }
    }
    
    @AfterClass
    public static void tearDown() throws Exception {
        try {
            if (solr != null) {
                solr.rollback();
            }
        } catch (SolrException e) {
        }
        // This is needed to avoid LockObtainedException when running the whole test suite,
        // but it sometimes causes warnings about too many close() calls ... 
        while (solrCore != null && ! solrCore.isClosed()) {
            solrCore.close();
        }
        if (coreContainer != null) {
            coreContainer.shutdown();
        }
    }
    
    protected void assertQuery (Object result, String query) throws Exception {
        assertQuery (result, null, query);
    }

    protected void assertQuery (Object result, String type, String query) throws Exception {
        SolrQuery q = new SolrQuery(query);
        q.setRequestHandler(SOLR_QUERY_TYPE);
        QueryResponse rsp = solr.query(q, METHOD.POST);
        NamedList<Object> response = rsp.getResponse();
        NamedList<?> actual = (NamedList<?>) response.get("xpath-results");
        if (result == null) {
            assertEquals (0, actual.size());
        } else {
            assertNotNull ("no result", actual);
            assertEquals ("no result", 1, actual.size());
            assertEquals (result, actual.getVal(0));
        }
        if (type != null) {
            assertEquals (type, actual.getName(0));
        }
    }

    protected void assertQueryCount(int count, String query, SolrServer core) throws SolrServerException {
        SolrQuery q = new SolrQuery(query);
        QueryResponse rsp = core.query(q);
        assertEquals(count, rsp.getResults().getNumFound());
    }

    protected void assertQueryCount(int count, String query) throws SolrServerException {
        assertQueryCount (count, query, solr); 
    }

    protected void assertXPathSearchCount(int count, int docCount, String type, String value, String query) throws SolrServerException {
        assertXPathSearchCount (count, docCount, type, value, query, solr);
    }
    
    protected void assertXPathSearchCount(int count, int docCount, String type, String value, String query, SolrServer core)
            throws SolrServerException {
        assertXPathSearchCount(count, docCount, 10, type, value, query, core);
    }

    protected void assertXPathSearchError(String error, String query) throws SolrServerException {
        SolrQuery q = new SolrQuery(query);
        q.setRequestHandler(SOLR_QUERY_TYPE);
        QueryResponse rsp = solr.query(q, METHOD.POST);
        String actualError = rsp.getResponse().get("xpath-error").toString();
        assertTrue("Error " + actualError + " does not contain expected error " + error, actualError.contains(error));
    }

    protected void assertXPathSearchCount(int count, int docCount, int maxResults, String type, String value,
            String query) throws SolrServerException {
        assertXPathSearchCount (count, docCount, maxResults, type, value, query, solr);
    }
    
    protected void assertXPathSearchCount(int count, int docCount, int maxResults, String type, String value,
            String query, SolrServer core) throws SolrServerException {
        SolrQuery q = new SolrQuery(query);
        q.setRequestHandler(SOLR_QUERY_TYPE);
        q.setRows(maxResults);
        q.setStart(0);
        QueryResponse rsp = core.query(q, METHOD.POST);
        NamedList<?> results = (NamedList<?>) rsp.getResponse().get("xpath-results");
        String error = (String) rsp.getResponse().get("xpath-error");
        if (type.equals("error")) {
            assertEquals(value, error);
        } else {
            assertNull("got unexpected error: " + error, error);
            long docMatches = rsp.getResults().getNumFound();
            assertEquals("unexpected number of documents retrieved", docCount, docMatches);
            assertEquals("unexpected result count", count, results.size());
            assertEquals("unexpected result type", type, results.getName(0));
            String returnValue = results.getVal(0).toString();
            if (returnValue.startsWith("<")) {
                // assume the returned value is an element - hack to avoid real
                // parsing
                assertEquals(value, returnValue.substring(1, returnValue.indexOf('>')));
            } else {
                assertEquals(value, returnValue);
            }
        }
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
