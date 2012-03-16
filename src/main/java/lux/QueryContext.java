package lux;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.jaxen.Context;
import org.jaxen.ContextSupport;

public class QueryContext extends Context {
    
    private Query query;
    private IndexSearcher searcher;
    private String xmlFieldName = "xml_text";
    
    public QueryContext (ContextSupport contextSupport, IndexSearcher searcher, Query query) {
        this (contextSupport, searcher);
        this.query = query;
    }

    public QueryContext(ContextSupport contextSupport, IndexSearcher searcher) {
        super(contextSupport);
        this.searcher = searcher;
    }
    
    public Query getQuery () {
        return query;
    }
    
    public IndexSearcher getSearcher () {
        return searcher;
    }
    
    public String getXmlFieldName () {
        return xmlFieldName;
    }
    
}
