package lux;

import java.io.IOException;
import java.io.StringReader;

import lux.api.QueryStats;
import lux.api.ValueType;
import lux.xml.XmlBuilder;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Scorer;

public class XPathCollector extends Collector {

    private final ResultList<Object> results;
    private final QueryStats queryStats;
    private final int start;
    private final int size;
    private final XmlBuilder builder;
    private int docCount = 0;
    private int resultCount = 0;
    private IndexReader reader;
    private boolean isCounting;
    private boolean isMinimal;
    
    /**
     * Create a result collector for the given expression; collects a page of the total result set.
     * @param saxon the saxon evaluator
     * @param expr the xpath expression to be evaluated
     * @param start the offset of the first result to collect (1-based)
     * @param size the size of the result page; no more than size results will be collected
     * @param queryStats query stats will be deposited here
     */
    public XPathCollector (XPathQuery query, int start, int size, XmlBuilder builder, QueryStats queryStats) {
        this.results = new ResultList<Object>();
        this.queryStats = queryStats;
        this.builder = builder;
        isCounting = query.isFact(XPathQuery.COUNTING);
        isMinimal = query.isFact(XPathQuery.MINIMAL);
        this.start = (isCounting ? Integer.MAX_VALUE : start);
        if (query.getResultType() == ValueType.BOOLEAN) {
            // Short-circuit evaluation of boolean expressions across the whole query set
            this.size = 1;
        } else {
            this.size = size;
        }
    }
    
    /** Create a result collector for the given expression that collects all results */
    public XPathCollector (XPathQuery query, XmlBuilder builder, QueryStats queryStats) {
        this (query, 1, Integer.MAX_VALUE, builder, queryStats);
    }
    
    @Override
    public void setScorer(Scorer scorer) throws IOException {
    }

    @Override
    public void collect(int docnum) throws IOException {
        ++ docCount;
        if (isCounting && isMinimal) {
            return;
        }
        if (docCount < start) {
            return;
        }
            
        long t = System.nanoTime();
        
        String xmlFieldName = "xml_text";
        Document document = reader.document(docnum, new SingleFieldSelector(xmlFieldName));
        String xml = document.get(xmlFieldName);
        Object doc;
        doc = builder.build(new StringReader (xml));
        results.add (doc);
        t = System.nanoTime() - t;
        queryStats.collectionTime += t;
        if (results.size() >= size) {
            throw new ShortCircuitException();
        }
    }

    @Override
    public void setNextReader(IndexReader reader, int docBase) throws IOException {
        this.reader = reader;
    }

    @Override
    public boolean acceptsDocsOutOfOrder() {
        return true;
    }
    
    public int getDocCount () {
        return docCount;
    }

    public ResultList<?> getResults() {
        if (isCounting && results.size() == 0) {
            if (isMinimal) {
                results.add(Double.valueOf(docCount));
            } else {
                results.add(Double.valueOf(resultCount));
            }
        }            
        return results;
    }
    
}
