package lux.solr;

import java.io.IOException;
import java.util.List;

import lux.IndexTestSupportBase;

import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;

public class CloudIndexSupport extends IndexTestSupportBase {
    
    SolrServer control;
    List<SolrServer> clients;
    
    CloudIndexSupport (SolrServer control, List<SolrServer> clients) {
        this.clients = clients;
        this.control = control;
    }

    @Override
    protected void addDocument(String uri, String xml) throws IOException {
        int which = (uri.toString().hashCode() & 0x7fffffff) % clients.size();
        SolrServer client = clients.get(which);
        SolrInputDocument doc = new SolrInputDocument();
        doc.addField("lux_uri", uri);
        doc.addField("lux_xml", xml);
        try {
            client.add(doc);
            control.add(doc);
        } catch (SolrServerException e) {
            throw new IOException(e);
        }
     }

    @Override
    protected void commit() throws IOException {
        try {
            control.commit();
            for (SolrServer client : clients) {
                client.commit();
            }       
        } catch (SolrServerException e) {
            throw new IOException (e);
        }
    }

}
