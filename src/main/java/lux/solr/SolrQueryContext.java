package lux.solr;

import lux.QueryContext;

import org.apache.solr.handler.component.ResponseBuilder;

public class SolrQueryContext extends QueryContext {

    private final XQueryComponent queryComponent;
    
    private ResponseBuilder responseBuilder;

    public SolrQueryContext(XQueryComponent xQueryComponent) {
        this.queryComponent = xQueryComponent;
    }

    public XQueryComponent getQueryComponent() {
        return queryComponent;
    }

    public ResponseBuilder getResponseBuilder() {
        return responseBuilder;
    }

    public void setResponseBuilder(ResponseBuilder responseBuilder) {
        this.responseBuilder = responseBuilder;
    }

}
