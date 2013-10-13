package lux.solr;

import static lux.index.IndexConfiguration.*;

import java.io.IOException;
import java.util.List;

import lux.IndexTestSupportBase;
import lux.index.field.FieldDefinition.Type;
import lux.index.field.XPathField;

import org.apache.lucene.document.Field.Store;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;

public class CloudIndexSupport extends IndexTestSupportBase {
    
    SolrServer control;
    List<SolrServer> clients;
    
    CloudIndexSupport (SolrServer control, List<SolrServer> clients) {
        super (INDEX_QNAMES|INDEX_PATHS|STORE_DOCUMENT|INDEX_FULLTEXT|INDEX_EACH_PATH);
        
        //indexer.getConfiguration().addField(new XPathField("doctype", "name(/*)", null, Store.YES, Type.STRING));
        //indexer.getConfiguration().addField(new XPathField("title", "/*/TITLE | /SPEECH/LINE[1]", null, Store.YES, Type.STRING));
        //indexer.getConfiguration().addField(new XPathField("title_multi", "//TITLE", null, Store.YES, Type.STRING));
        //indexer.getConfiguration().addField(new XPathField("actnum", "/*/@act", null, Store.YES, Type.INT));
        //indexer.getConfiguration().addField(new XPathField("scnlong", "/*/@scene", null, Store.YES, Type.LONG));

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
