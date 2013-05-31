package lux.solr;


import static org.junit.Assert.assertEquals;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import lux.xml.tinybin.TinyBinary;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.embedded.EmbeddedSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.core.CoreContainer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TLogTest {
    
    private TinyBinary schemaXml;
    
    private CoreContainer coreContainer;
    private CoreContainer.Initializer initializer;
    private Processor processor;
    private static final Charset UTF8 = Charset.forName("utf-8");
    
    @Before
    public void setup () {
        initializer = new CoreContainer.Initializer();
        coreContainer = initializer.initialize();
        processor = new Processor(false);
    }

    @After
    public void cleanup () {
        coreContainer.shutdown();
    }

    /*
     * Make sure we can replay transactions from the log by
     * write 
     * shut down
     * restart
     * commit
     */
    @Test 
    public void testTransactionLog () throws Exception {
        String defaultCoreName = coreContainer.getDefaultCoreName();
        SolrServer solr = new EmbeddedSolrServer(coreContainer, defaultCoreName);
        solr.deleteByQuery("*:*");
        solr.commit();
        
        // solrCore = coreContainer.getCore(defaultCoreName);

        // add some documents
        Collection<SolrInputDocument> docs = new ArrayList<SolrInputDocument> ();
        BaseSolrTest.addSolrDocFromFile("src/test/resources/conf/schema.xml", docs);
        BaseSolrTest.addSolrDocFromFile("src/test/resources/conf/solrconfig.xml", docs);
        solr.add(docs);
        
        QueryResponse response = search ("lux_uri:src/test/resources/conf/schema.xml", solr);
        assertEquals (0, response.getResults().getNumFound());

        // soft commit -- note must waitSearcher in order to see commit
        solr.commit(false, true, true);
        response = search ("lux_uri:src/test/resources/conf/schema.xml", solr);
        assertEquals (1, response.getResults().getNumFound());
        assertEquals ("src/test/resources/conf/schema.xml", response.getResults().get(0).get("lux_uri"));
        List<?> xml = (List<?>) response.getResults().get(0).get("lux_xml");
        schemaXml = new TinyBinary ((byte[]) xml.get(0), UTF8);
        
        // shut down
        solr.shutdown();
        coreContainer.shutdown();
        
        // start up again
        coreContainer = initializer.initialize();
        solr = new EmbeddedSolrServer(coreContainer, defaultCoreName);
        
        // retrieve the documents (from the transaction log):
        validateContent (solr);
        
        // commit
        solr.commit();
        validateContent (solr);
        
    }
    
    private void validateContent (SolrServer solr) throws SolrServerException {
        QueryResponse response = search ("*:*", solr);
        assertEquals (2, response.getResults().getNumFound());
        assertEquals ("src/test/resources/conf/schema.xml", response.getResults().get(0).get("lux_uri"));
        List<?> xml = (List<?>) response.getResults().get(0).get("lux_xml");
        TinyDocumentImpl schema = schemaXml.getTinyDocument(processor.getUnderlyingConfiguration());
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
