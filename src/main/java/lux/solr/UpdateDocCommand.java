package lux.solr;

import org.apache.lucene.document.Document;
import org.apache.lucene.util.BytesRef;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.update.AddUpdateCommand;

public class UpdateDocCommand extends AddUpdateCommand {
    
    private Document doc;

    public UpdateDocCommand(SolrCore core, Document doc, String uri) {
        super(makeSolrRequest(core));
        this.doc = doc;
        setIndexedId(new BytesRef(uri));
    }
    
    public UpdateDocCommand(SolrQueryRequest req, SolrInputDocument sdoc, Document doc, String uri) {
        super(req);
        this.doc = doc;
        this.solrDoc = sdoc;
        setIndexedId(new BytesRef(uri));
    }
    
    @Override
    public Document getLuceneDocument () {
        return doc;
    }

    private static SolrQueryRequest makeSolrRequest(SolrCore core) {
        SolrParams params = new ModifiableSolrParams () {};
        return new SolrQueryRequestBase(core, params) {};
    }

}
