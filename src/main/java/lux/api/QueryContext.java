package lux.api;

import lux.index.XmlIndexer;
import lux.lucene.LuxSearcher;

import org.apache.lucene.search.Query;

public class QueryContext {

    private final Query query;
    private final LuxSearcher searcher;
    private final XmlIndexer indexer;

    public QueryContext (Query query, LuxSearcher searcher, XmlIndexer indexer) {
        this.query = query;
        this.searcher = searcher;
        this.indexer = indexer;
    }
    
    public QueryContext (LuxSearcher searcher, XmlIndexer indexer) {
        this (null, searcher, indexer);
    }
    
    public Query getQuery() {
        return query;
    }

    public LuxSearcher getSearcher() {
        return searcher;
    }
    
    public XmlIndexer getIndexer () {
        return indexer;
    }

}