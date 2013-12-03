package lux.solr;


import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import lux.xml.tinybin.TinyBinary;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;

import org.apache.commons.io.FileUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.core.CoreContainer;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class TLogTest extends BaseSolrTest {
    
    private TinyBinary schemaXml;
    
    private Processor processor;
    private static final Charset UTF8 = Charset.forName("utf-8");
    
    @Before
    public void init () throws IOException {
        processor = new Processor(false);
    }

    /*
     * Make sure we can replay transactions from the log by
     * write 
     * shut down
     * restart
     * commit
     * What made this test go south all of a sudden?
     */
    @Test @Ignore
    public void testTransactionLog () throws Exception {
        solrCore = null;

        // add some documents
        Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument> ();
        BaseSolrTest.addSolrDocFromFile("src/test/resources/conf/schema.xml", docs);
        BaseSolrTest.addSolrDocFromFile("src/test/resources/conf/solrconfig.xml", docs);
        solr.add(docs);
        
        QueryResponse response = search ("lux_uri:src/test/resources/conf/schema.xml", solr);
        assertEquals (0, response.getResults().getNumFound());

        // soft commit -- note must waitSearcher in order to see commit
        // we want it in the tlog, but not saved out to the index yet
        solr.commit(true, true, true);
        response = search ("lux_uri:src/test/resources/conf/schema.xml", solr);
        assertEquals (1, response.getResults().getNumFound());
        assertEquals ("src/test/resources/conf/schema.xml", response.getResults().get(0).get("lux_uri"));
        List<?> xml = (List<?>) response.getResults().get(0).get("lux_xml");
        schemaXml = new TinyBinary ((byte[]) xml.get(0), UTF8);
        
        // copy contents of solr data folder to temporary area to simulate hard shutdown
        copyDirectory ("solr/collection1/data/tlog", "solr/tlog-backup");

        System.out.println ("shut down solr");
        
        // shut down
        coreContainer.shutdown();
        solr.shutdown();
        File lock = new File("solr/collection1/data/index/write.lock");
        if (lock.exists()) {
            System.err.println ("solr did not shut down cleanly");
            assertTrue (lock.delete());
        }

        // restore contents of data directory to before we shutdown
        removeDirectory ("solr/collection1/data/tlog");
        copyDirectory ("solr/tlog-backup", "solr/collection1/data/tlog");
        removeDirectory ("solr/tlog-backup");
        
        System.out.println ("start solr up again");

        // start up again
        coreContainer = new CoreContainer();
        coreContainer.load();
        solr = new EmbeddedSolrServer(coreContainer, "collection1");

        // retrieve the documents (from the replayed transaction log):
        validateContent (solr);
        
        // commit
        solr.commit();
        validateContent (solr);
        
    }
    
    private void removeDirectory(String directory) throws IOException {
        FileUtils.deleteDirectory(new File(directory));
    }
    
    /*
    private void cleanDirectory(String directory) throws IOException {
        FileUtils.cleanDirectory(new File(directory));
    }
     */
    
    private void copyDirectory(String srcDir, String destDir) throws IOException {
        FileUtils.copyDirectory(new File(srcDir), new File(destDir));
    }

    private void validateContent (SolrServer solr) throws SolrServerException {
        QueryResponse response = search ("*:*", solr);
        assertEquals (2, response.getResults().getNumFound());
        assertEquals ("src/test/resources/conf/schema.xml", response.getResults().get(0).get("lux_uri"));
        List<?> xml = (List<?>) response.getResults().get(0).get("lux_xml");
        TinyDocumentImpl schema = schemaXml.getTinyDocument(processor.getUnderlyingConfiguration());
        assertNotNull ("no xml stored for schema.xml", xml);
        TinyBinary retrieved = new TinyBinary ((byte[]) xml.get(0), UTF8);
        TinyDocumentImpl result = retrieved.getTinyDocument(processor.getUnderlyingConfiguration());
        assertEquals (new XdmNode(schema).toString(), new XdmNode(result).toString());
    }
    
    private QueryResponse search (String q, SolrServer solr) throws SolrServerException {
        SolrQuery query = new SolrQuery();
        query.setQuery (q);
        query.addField("lux_uri");
        query.addField("lux_xml");
        return solr.query(query);
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
