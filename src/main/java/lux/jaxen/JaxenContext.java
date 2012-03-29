package lux.jaxen;

import lux.api.QueryContext;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.jaxen.ContextSupport;
import org.jaxen.Context;

public class JaxenContext extends Context implements lux.api.Context {
    
    private final QueryContext queryContext;
    
    
    public JaxenContext (ContextSupport contextSupport, Query query, IndexSearcher searcher) {
        super (contextSupport);
        queryContext = new QueryContext (query, searcher);
    }

    public JaxenContext(ContextSupport contextSupport, IndexSearcher searcher) {
        this (contextSupport, null, searcher);
    }

    public QueryContext getQueryContext() {
        return queryContext;
    }

    public String getXmlFieldName() {
        return queryContext.getXmlFieldName();
    }
    
}
