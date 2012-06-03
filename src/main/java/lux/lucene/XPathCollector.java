package lux.lucene;

import java.io.IOException;
import java.io.StringReader;

import lux.ResultList;
import lux.XPathQuery;
import lux.api.QueryStats;
import lux.api.ValueType;
import lux.index.XmlField;
import lux.solr.ShortCircuitException;
import lux.xml.XmlBuilder;
import net.sf.saxon.s9api.XdmAtomicValue;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Scorer;

/**
 * TODO: is this code dead now?
 * TODO: refactor into multiple collectors for counting and boolean short-circuits
 * in order to eliminate some of the tests in the inner loop here.  Also - get rid
 * of the lazy initialization in getResults(); it's just confusing
 * @author sokolov
 *
 */
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
    // if non-null, return isExists == count(results)>0
    private Boolean isExists;
    
    /**
     * Create a result collector for the given expression; collects a page of the total result set.
     * @param saxon the saxon evaluator
     * @param expr the xpath expression to be evaluated
     * @param start the offset of the first result to collect (1-based) - 
     * ignored if COUNTING, BOOLEAN_TRUE, or BOOLEAN_FALSE
     * @param size the size of the result page; no more than size results will be collected -
     * ignored if COUNTING, BOOLEAN_TRUE, or BOOLEAN_FALSE
     * @param queryStats query stats will be deposited here
     */
    public XPathCollector (XPathQuery query, int start, int size, XmlBuilder builder, QueryStats queryStats) {
        this.results = new ResultList<Object>();
        this.queryStats = queryStats;
        this.builder = builder;
        isCounting = query.isFact(XPathQuery.COUNTING);
        isMinimal = query.isFact(XPathQuery.MINIMAL);
        if (query.getResultType().equals(ValueType.BOOLEAN)|| query.getResultType().equals(ValueType.BOOLEAN_FALSE)) {
            // Short-circuit evaluation of boolean expressions across the whole query set
            this.size = 1;
            this.start = Integer.MAX_VALUE;
            isExists = query.isFact(XPathQuery.BOOLEAN_TRUE);
        } else {
            this.size = size;
            this.start = (isCounting ? Integer.MAX_VALUE : start);
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
        
        long t = System.nanoTime();
        ++ docCount;
        if (docCount < start) {
            if (isExists != null) {
                throw new ShortCircuitException();
            }
            return;
        }
        long t1 = System.nanoTime();
        String xmlFieldName = XmlField.XML_STORE.getName();
        Document document = reader.document(docnum, new SingleFieldSelector(xmlFieldName));
        String xml = document.get(xmlFieldName);
        String uri = document.get(XmlField.URI.getName());
        Object doc;
        doc = builder.build(new StringReader (xml), uri);
        results.add (doc);
        if (queryStats != null) {
            long t2 = System.nanoTime();
            queryStats.retrievalTime += (t2 - t1);
            queryStats.collectionTime += (t2 - t);
        }
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
        if (results.isEmpty()) {
            if (isCounting) {
                if (isMinimal) {
                    results.add(new XdmAtomicValue(docCount));
                } else {
                    results.add(new XdmAtomicValue(resultCount));
                }
            }
            else if (isExists != null) {
                results.add(new XdmAtomicValue(isExists == (docCount > 0)));
            }
        }
        return results;
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
