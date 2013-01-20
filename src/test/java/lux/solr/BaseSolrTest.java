package lux.solr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.CoreContainer;
import org.junit.BeforeClass;

public class BaseSolrTest {

    protected static SolrServer solr;
    public final String SOLR_QUERY_TYPE = "/xquery";

    @BeforeClass
    public static void setup() throws Exception {
        System.setProperty("solr.solr.home", "solr");
        CoreContainer.Initializer initializer = new CoreContainer.Initializer();
        CoreContainer coreContainer = initializer.initialize();
        solr = new EmbeddedSolrServer(coreContainer, "");
        solr.deleteByQuery("*:*");
    }

    public BaseSolrTest() {
        super();
    }
    
    protected void assertQuery (String result, String query) throws Exception {
        SolrQuery q = new SolrQuery(query);
        q.setQueryType(SOLR_QUERY_TYPE);
        QueryResponse rsp = solr.query(q, METHOD.POST);
        NamedList<Object> response = rsp.getResponse();
        NamedList<?> actual = (NamedList<?>) response.get("xpath-results");
        if (result == null) {
            assertEquals (0, actual.size());
        } else {
            assertEquals (result, actual.getVal(0));
        }
    }

    protected void assertQueryCount(int count, String query) throws SolrServerException {
        SolrQuery q = new SolrQuery(query);
        QueryResponse rsp = solr.query(q);
        assertEquals(count, rsp.getResults().getNumFound());
    }

    protected void assertXPathSearchCount(int count, int docCount, String type, String value, String query)
            throws SolrServerException {
        assertXPathSearchCount(count, docCount, 10, type, value, query);
    }

    protected void assertXPathSearchError(String error, String query) throws SolrServerException {
        SolrQuery q = new SolrQuery(query);
        q.setQueryType(SOLR_QUERY_TYPE);
        QueryResponse rsp = solr.query(q, METHOD.POST);
        String actualError = rsp.getResponse().get("xpath-error").toString();
        assertTrue("Error " + actualError + " does not contain expected error " + error, actualError.contains(error));
    }

    protected void assertXPathSearchCount(int count, int docCount, int maxResults, String type, String value,
            String query) throws SolrServerException {
        SolrQuery q = new SolrQuery(query);
        q.setQueryType(SOLR_QUERY_TYPE);
        q.setRows(maxResults);
        q.setStart(0);
        QueryResponse rsp = solr.query(q, METHOD.POST);
        NamedList<?> results = (NamedList<?>) rsp.getResponse().get("xpath-results");
        String error = (String) rsp.getResponse().get("xpath-error");
        if (type.equals("error")) {
            assertEquals(value, error);
        } else {
            long docMatches = rsp.getResults().getNumFound();
            assertNull("got unexpected error: " + error, error);
            assertEquals(docCount, docMatches);
            assertEquals(count, results.size());
            assertEquals(type, results.getName(0));
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