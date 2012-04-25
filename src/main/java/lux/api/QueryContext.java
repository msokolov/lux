package lux.api;

import lux.lucene.LuxSearcher;
import org.apache.lucene.search.Query;

public class QueryContext {

    private final Query query;
    private final LuxSearcher searcher;
    private final String xmlFieldName = "xml_text";

    public QueryContext (Query query, LuxSearcher searcher) {
        this.query = query;
        this.searcher = searcher;
    }
    
    public QueryContext (LuxSearcher searcher) {
        this (null, searcher);
    }
    
    public Query getQuery() {
        return query;
    }

    public LuxSearcher getSearcher() {
        return searcher;
    }

    public String getXmlFieldName() {
        return xmlFieldName;
    }

}