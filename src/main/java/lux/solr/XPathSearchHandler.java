package lux.solr;

import java.util.List;


import org.apache.solr.handler.component.QueryComponent;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;

// or maybe define a custom SearchComponent???

public class XPathSearchHandler extends SearchHandler {
    
    public void handleRequest(SolrQueryRequest req, SolrQueryResponse rsp) {
        super.handleRequest(req, rsp);
    }
    
    @Override protected List<String> getDefaultComponents() {
        List<String> defaults = super.getDefaultComponents();
        defaults.remove(QueryComponent.COMPONENT_NAME);
        defaults.add(0, XPathSearchComponent.COMPONENT_NAME);
        return defaults;
    }
    
}
