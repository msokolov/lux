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
import org.apache.solr.common.SolrDocumentList;
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
        setup (solrHome, "collection1");
    }
    
    protected static void setup(String solrHome, String coreName) throws Exception {
        System.setProperty("solr.solr.home", solrHome);
        File index = new File (solrHome + "/" + coreName + "/data/index/");
        if (index.exists()) {
            FileUtils.cleanDirectory(index);
        }
        File tlog= new File (solrHome + "/" + coreName + "/data/tlog/");
        if (tlog.exists()) {
            FileUtils.cleanDirectory(tlog);
        }
        coreContainer = new CoreContainer (solrHome);
        coreContainer.load();
        solr = new EmbeddedSolrServer(coreContainer, coreName);
        solrCore = coreContainer.getCore(coreName);
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
        solrCore.close();
        if (coreContainer != null) {
            coreContainer.shutdown();
        }
        FileUtils.cleanDirectory(new File(solrCore.getDataDir() + "/index"));
        FileUtils.cleanDirectory(new File(solrCore.getDataDir() + "/tlog"));
    }
    
    protected void assertQuery (Object result, String query) throws Exception {
        assertQuery (result, null, query);
    }

    protected void assertSolrQuery (Object result, String fld, String query) throws Exception {
        // check the field value of a result returned in the usual Solr way
        SolrQuery q = new SolrQuery(query);
        QueryResponse rsp = solr.query(q, METHOD.POST);
        NamedList<Object> response = rsp.getResponse();
        SolrDocumentList docs = (SolrDocumentList) response.get("response");
        if (result == null) {
            assertEquals (0, docs.size());
        } else {
            assertNotNull ("no docs returned", docs);
            assertEquals ("unexpected result count", 1, docs.size());
            assertEquals (result, docs.get(0).get(fld));
        }
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

    protected void assertSolrQueryCount(int count, String query, SolrServer core) throws SolrServerException {
        SolrQuery q = new SolrQuery(query);
        QueryResponse rsp = core.query(q);
        assertEquals(count, rsp.getResults().getNumFound());
    }

    protected void assertSolrQueryCount(int count, String query) throws SolrServerException {
        assertSolrQueryCount (count, query, solr); 
    }

    protected void assertQueryCount(int count, int docCount, String type, String value, String query) throws SolrServerException {
        assertQueryCount (count, docCount, type, value, query, solr);
    }
    
    protected void assertQueryCount(int count, int docCount, String type, String value, String query, SolrServer core)
            throws SolrServerException {
        assertQueryCount(count, docCount, 10, type, value, query, core);
    }

    protected void assertQueryError(String error, String query) throws SolrServerException {
        SolrQuery q = new SolrQuery(query);
        q.setRequestHandler(SOLR_QUERY_TYPE);
        QueryResponse rsp = solr.query(q, METHOD.POST);
        String actualError = rsp.getResponse().get("xpath-error").toString();
        assertTrue("Error " + actualError + " does not contain expected error " + error, actualError.contains(error));
    }

    protected void assertQueryCount(int count, String query) throws SolrServerException {
        assertQueryCount (count, count, 100, null, null, query, solr);
    }
    
    protected void assertQueryCount(int count, int docCount, int maxResults, String type, String value,
            String query) throws SolrServerException {
        assertQueryCount (count, docCount, maxResults, type, value, query, solr);
    }
    
    protected void assertQueryCount(int count, int docCount, int maxResults, String type, String value,
            String query, SolrServer core) throws SolrServerException {
        SolrQuery q = new SolrQuery(query);
        q.setRequestHandler(SOLR_QUERY_TYPE);
        q.setRows(maxResults);
        q.setStart(0);
        QueryResponse rsp = core.query(q, METHOD.POST);
        NamedList<?> results = (NamedList<?>) rsp.getResponse().get("xpath-results");
        String error = (String) rsp.getResponse().get("xpath-error");
        if ("error".equals(type)) {
            assertEquals(value, error);
        } else {
            assertNull("got unexpected error: " + error, error);
            long docMatches = rsp.getResults().getNumFound();
            assertEquals("unexpected number of documents retrieved", docCount, docMatches);
            assertEquals("unexpected result count", count, results.size());
            if (count > 0) {
                if (type != null) {
                    assertEquals("unexpected result type", type, results.getName(0));
                }
                if (value != null) {
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
        }
    }

    // insert docs using the standard field names: lux_uri,lux:xml 
    static void addSolrDocFromFile(String path, Collection<SolrInputDocument> docs) throws FileNotFoundException, IOException {
        addSolrDocFromFile (path, docs, URI, LUX_XML);
    }
    
    // insert docs using the provided field names
    static void addSolrDocFromFile(String path, Collection<SolrInputDocument> docs, String uriFieldName, String xmlFieldName) throws FileNotFoundException, IOException {
        SolrInputDocument doc = new SolrInputDocument(); 
        doc.addField (uriFieldName, path);
        FileInputStream in = new FileInputStream (path);
        String buf = IOUtils.toString(in);
        doc.addField(xmlFieldName, buf);
        docs.add(doc);
    }
    
    static void addSolrDoc(String uri, String text, Collection<SolrInputDocument> docs) throws FileNotFoundException, IOException {
        // add doc using default field names
        addSolrDoc (uri, text, docs, URI, LUX_XML);
    }

    static void addSolrDoc(String uri, String text, Collection<SolrInputDocument> docs, String uriFieldName, String xmlFieldName) throws FileNotFoundException, IOException {
        SolrInputDocument doc = new SolrInputDocument(); 
        doc.addField (uriFieldName, uri);
        doc.addField (xmlFieldName, text);
        docs.add (doc);
    }

}
