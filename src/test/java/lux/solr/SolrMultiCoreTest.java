package lux.solr;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.common.SolrInputDocument;
import org.junit.BeforeClass;
import org.junit.Test;

public class SolrMultiCoreTest extends BaseSolrTest {
    
    protected static SolrServer core1, core2;

    @BeforeClass 
    public static void setup() throws Exception {

        BaseSolrTest.setup("solr-multi");
        
        core1 = new EmbeddedSolrServer(coreContainer, "core1");
        core1.deleteByQuery("*:*");
        
        core2 = new EmbeddedSolrServer(coreContainer, "core2");
        core2.deleteByQuery("*:*");
        
        Collection<SolrInputDocument> odd_docs = new ArrayList<SolrInputDocument> ();
        Collection<SolrInputDocument> even_docs = new ArrayList<SolrInputDocument> ();
        for (int i = 1; i <= 100; i++) {
            if (i % 2 == 1) {
                addSolrDoc ("test" + i, "<doc><title id='" + i + "'>" + (101-i) + "</title><test>cat</test></doc>", odd_docs);
            } else {
                addSolrDoc ("test" + i, "<doc><title id='" + i + "'>" + (101-i) + "</title><test>cat</test></doc>", even_docs);
            }
        }
        core1.add (odd_docs);
        core2.add (even_docs);        
        core1.commit();
        core2.commit();
    }
    
    @Test
    public void testMultipleCores () throws SolrServerException {
        // We can start two cores with different documents:
        assertQueryCount (50, "*:*", core1);
        assertQueryCount (50, "*:*", core2);
        assertQueryCount (1, "lux_uri:test1", core1);
        assertQueryCount (0, "lux_uri:test1", core2);
        // We can run xquery against them:
        // relies on documents like: <doc id="1">100</doc>, <doc id="2">99</doc>
        assertXPathSearchCount (1, 1, "xs:double", "99.0", "number((/doc/title)[1])", core2);
        assertXPathSearchCount (1, 1, "xs:double", "100.0", "number((/doc/title)[1])", core1);
    }
    
}
