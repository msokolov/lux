package lux;

import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.core.CoreContainer;
import org.junit.BeforeClass;
import org.junit.Test;

public class IndexedXPathTest {
    
    private static SolrServer solr;
    
    @BeforeClass public static void setup () throws Exception {
        System.setProperty("solr.solr.home", "solr");
        CoreContainer.Initializer initializer = new CoreContainer.Initializer();
        CoreContainer coreContainer = initializer.initialize();
        solr = new EmbeddedSolrServer(coreContainer, "");
        Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument> ();
        addSolrDoc("solr/conf/schema.xml", docs);
        addSolrDoc("solr/conf/solrconfig.xml", docs);
        solr.add (docs);
        solr.commit();     
    }
    
    @Test public void testIndex() throws Exception {
        // make sure the documents have the values we expect
        assertQueryCount (2, "*:*");
        assertQueryCount (1, "lux_elt_name_ms:config");
        assertQueryCount (1, "lux_elt_name_ms:schema");
        assertQueryCount (2, "lux_elt_name_ms:str");
        assertQueryCount (1, "lux_path_ms:/schema/types/fieldType");
        assertQueryCount (1, "lux_path_ms:/config/luceneMatchVersion");
    }
    
    @Test public void testXPathQuery() throws Exception {
        // test search using standard search query handler, custom query parser
        // This is a bit like: returns
        assertQueryCount (1, "{!type=xpath}//config");
    }
    
    @Test public void testXPathSearch() throws Exception {
        // test search using standard search query handler, custom query parser
        assertXPathSearchCount (1, "//config");
    }
    
    private void assertQueryCount (int count, String query) throws SolrServerException {
        SolrQuery q = new SolrQuery(query);
        QueryResponse rsp = solr.query (q);
        assertEquals (count, rsp.getResults().getNumFound());       
    }
    
    private void assertXPathSearchCount (int count, String query) throws SolrServerException {
        SolrQuery q = new SolrQuery(query);
        q.setParam("qt", "/xpath");
        QueryResponse rsp = solr.query (q);
        assertEquals (count, rsp.getResults().getNumFound());       
    }

    private static void addSolrDoc(String path, Collection<SolrInputDocument> docs) throws FileNotFoundException, IOException {
        SolrInputDocument doc = new SolrInputDocument(); 
        doc.addField ("uri", path);
        FileInputStream in = new FileInputStream (path);
        String buf = IOUtils.toString(in);
        doc.addField("xml_text", buf);
        docs.add(doc);
    }
    
}
