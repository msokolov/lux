package lux.solr;

import static org.junit.Assert.assertEquals;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import lux.index.FieldName;

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
                addSolrDocAltFields("test" + i, "<doc><title id='" + i + "'>" + (101-i) + "</title><test>cat</test></doc>", even_docs);
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
        assertQueryCount (0, "uri:test1", core2);
        // We can run xquery against them:
        // relies on documents like: <doc id="1">100</doc>, <doc id="2">99</doc>
        assertXPathSearchCount (1, 1, "xs:double", "99.0", "number((/doc/title)[1])", core2);
        assertXPathSearchCount (1, 1, "xs:double", "100.0", "number((/doc/title)[1])", core1);
    }

    /*
     * Ensure that field renaming propagates down to the compiler, which may cache the URI field name at least.
     */
    @Test
    public void testRenameFields () throws Exception {
        SolrIndexConfig config1 = (SolrIndexConfig) coreContainer.getCore("core1").getInfoRegistry().get(SolrIndexConfig.class.getName());
    
        assertEquals ("lux_uri", config1.getCompiler().getUriFieldName());
        assertEquals ("lux_xml", config1.getIndexConfig().getFieldName(FieldName.XML_STORE));

        SolrIndexConfig config2 = (SolrIndexConfig) coreContainer.getCore("core2").getInfoRegistry().get(SolrIndexConfig.class.getName());
        assertEquals ("uri", config2.getCompiler().getUriFieldName());
        assertEquals ("xml", config2.getIndexConfig().getFieldName(FieldName.XML_STORE));
    }

    static void addSolrDocAltFields(String uri, String text, Collection<SolrInputDocument> docs) throws FileNotFoundException, IOException {
        SolrInputDocument doc = new SolrInputDocument(); 
        doc.addField ("uri", uri);
        doc.addField ("xml", text);
        docs.add(doc);
    }
}
