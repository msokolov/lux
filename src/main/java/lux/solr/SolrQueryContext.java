package lux.solr;

import javax.servlet.http.HttpServletRequest;

import lux.QueryContext;

import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;

public class SolrQueryContext extends QueryContext {

    public static final String LUX_HTTP_SERVLET_REQUEST = "lux.httpServletRequest";
    public static final String LUX_HTTP_SERVLET_RESPONSE = "lux.httpServletResponse";
    public static final String LUX_COMMIT = "lux.commit";

    private final XQueryComponent queryComponent;
    
    private final SolrQueryRequest req;
    
    private final HttpServletRequest servletRequest;

    private ResponseBuilder responseBuilder;
    
    private boolean commitPending;

    public SolrQueryContext(XQueryComponent xQueryComponent, SolrQueryRequest req) {
        this.queryComponent = xQueryComponent;
        this.req = req;
        servletRequest = (HttpServletRequest) req.getContext().get(LUX_HTTP_SERVLET_REQUEST);
    }

    public XQueryComponent getQueryComponent() {
        return queryComponent;
    }

    public ResponseBuilder getResponseBuilder() {
        return responseBuilder;
    }
    
    public HttpServletRequest getHttpServletRequest () {
        return servletRequest;
    }

    public void setResponseBuilder(ResponseBuilder responseBuilder) {
        this.responseBuilder = responseBuilder;
    }

    public SolrQueryRequest getSolrQueryRequest() {
        return req;
    }
    
    public boolean isCommitPending() {
        return commitPending;
    }

    public void setCommitPending(boolean commitPending) {
        this.commitPending = commitPending;
    }
}
