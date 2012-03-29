package lux.api;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

public class QueryContext {

    private final Query query;
    private final IndexSearcher searcher;
    private final String xmlFieldName = "xml_text";

    public QueryContext (Query query, IndexSearcher searcher) {
        this.query = query;
        this.searcher = searcher;
    }
    
    public QueryContext (IndexSearcher searcher) {
        this (null, searcher);
    }
    
    public Query getQuery() {
        return query;
    }

    public IndexSearcher getSearcher() {
        return searcher;
    }

    public String getXmlFieldName() {
        return xmlFieldName;
    }

}