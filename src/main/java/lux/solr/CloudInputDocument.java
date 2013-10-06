package lux.solr;

import net.sf.saxon.om.NodeInfo;

import org.apache.solr.common.SolrInputDocument;

public class CloudInputDocument extends SolrInputDocument {
    
    private final NodeInfo documentNode;
    
    public CloudInputDocument (NodeInfo node) {
        this.documentNode = node;
    }
    
    public NodeInfo getDocumentNode () {
        return documentNode;
    }

}
