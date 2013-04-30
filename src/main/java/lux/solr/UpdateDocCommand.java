package lux.solr;

import org.apache.lucene.document.Document;
import org.apache.solr.core.SolrCore;
import org.apache.solr.update.AddUpdateCommand;

public class UpdateDocCommand extends AddUpdateCommand {
    
    public UpdateDocCommand(SolrCore core, Document doc, String uri) {
        this.doc = doc;
        this.indexedId = uri;
    }
    
}
