package lux;

import java.io.IOException;
import java.io.StringReader;

import lux.api.Evaluator;
import lux.api.Expression;
import lux.api.QueryStats;
import lux.api.ResultSet;
import lux.api.ValueType;
import lux.xml.XmlBuilder;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Scorer;

public class XPathCollector extends Collector {

    private final Evaluator evaluator;
    private final Expression expr;
    private final ResultList<Object> results;
    private final QueryStats queryStats;
    private final int start;
    private final int size;
    private int docCount = 0;
    private int resultCount = 0;
    private IndexReader reader;
    private boolean resultsAreDocuments;
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
    public XPathCollector (Evaluator evaluator, Expression expr, int start, int size, QueryStats queryStats) {
        this.evaluator = evaluator;
        this.results = new ResultList<Object>();
        this.queryStats = queryStats;
        this.expr = expr;
        resultsAreDocuments = expr.getXPathQuery().getResultType().equals(ValueType.DOCUMENT);
        isCounting = expr.getXPathQuery().isFact(XPathQuery.COUNTING);
        isMinimal = expr.getXPathQuery().isFact(XPathQuery.MINIMAL);
        this.start = (isCounting ? Integer.MAX_VALUE : start);
        if (expr.getXPathQuery().getResultType() == ValueType.BOOLEAN) {
            // Short-circuit evaluation of boolean expressions across the whole query set
            this.size = 1;
        } else {
            this.size = size;
        }
    }
    
    /** Create a result collector for the given expression that collects all results */
    public XPathCollector (Evaluator evaluator, Expression expr, QueryStats queryStats) {
        this (evaluator, expr, 1, Integer.MAX_VALUE, queryStats);
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
        long t = System.nanoTime();
        
        String xmlFieldName = evaluator.getContext().getXmlFieldName();
        //DocumentBuilder builder = saxon.getBuilder();
        XmlBuilder builder = evaluator.getBuilder();
        Document document = reader.document(docnum, new SingleFieldSelector(xmlFieldName));
        String xml = document.get(xmlFieldName);
        Object doc;
        doc = builder.build(new StringReader (xml));
        if (resultsAreDocuments) {
            results.add (doc);
        } else {
            for (Object item : evaluator.iterate(expr, doc)) {
                ++resultCount;
                if (resultCount >= start) {
                    results.add(item);
                    if (results.size() >= size) {
                        throw new ShortCircuitException();
                    }
                }
            }
        }
        t = System.nanoTime() - t;
        queryStats.collectionTime += t;
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

    public ResultSet<Object> getResults() {
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
