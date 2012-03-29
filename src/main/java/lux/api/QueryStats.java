package lux.api;

/**
 * A class that holds statistics about the last query execution
 */
public class QueryStats {
    /**
     * the number of documents that matched the lucene query. If XPath was executed (there wasn't
     * a short-circuited eval of some sort), this number of XML documents will have been retrieved
     * from the database and processed.
     */
    public int docCount;
    
    /**
     * time spent collecting results (parsing and computing xpath, mostly), in nanoseconds
     */
    public long collectionTime;
    
    /*
     * total time to evaluate the query and produce results, in nanoseconds
     */
    public long totalTime;
    
    /**
     * A description of the work done prior to collection; usu. the Lucene Query generated from the XPath and used to retrieve a set of candidate
     * documents for evaluation.
     */
    public String query;
}