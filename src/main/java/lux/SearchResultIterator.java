package lux;

import java.io.IOException;

import lux.exception.LuxException;
import lux.search.LuxSearcher;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;

public class SearchResultIterator implements SequenceIterator<NodeInfo> {
    
    private final DocIdSetIterator docIter;
    private final Query query;
    private final QueryStats stats;
    private final LuxSearcher searcher;
    private final String sortCriteria;
    private CachingDocReader docCache;
    private NodeInfo current = null;
    private int position = 0;
    
    public SearchResultIterator (Evaluator eval, Query query, String sortCriteria) throws IOException {
        this (eval.getSearcher(), eval.getDocReader(), eval.getQueryStats(), query, sortCriteria);
    }
    
    protected SearchResultIterator (LuxSearcher searcher, CachingDocReader docReader, QueryStats stats, Query query, String sortCriteria) throws IOException {
        this.query = query;
        this.searcher = searcher;
        this.docCache = docReader;
        this.stats = stats;
        this.sortCriteria = sortCriteria;
        if (stats != null) {
            stats.query = query.toString();
        }
        if (searcher == null) {
            throw new LuxException("Attempted to search using an Evaluator that has no searcher");
        }
        if (sortCriteria != null) {
            docIter = searcher.search(query, makeSortFromCriteria(sortCriteria));
        } else {
            docIter = searcher.searchOrdered(query);
        }
    }

    private Sort makeSortFromCriteria(String sortCriteria) {
        String[] fields = sortCriteria.split(",");
        SortField[] sortFields = new SortField [fields.length];
        for (int i = 0; i < fields.length; i++) {
            String [] tokens = fields[i].split("\\s+");
            boolean reverse = false;
            if (tokens.length > 1) {
                if (tokens[2].equals("descending")) {
                    reverse = true;
                } else if (!tokens[2].equals("ascending")) {
                    throw new LuxException ("Invalid sort key keyword: " + tokens[2] + " in: " + sortCriteria);
                }
                if (tokens.length > 2) {
                    throw new LuxException ("Invalid sort key: " + sortCriteria);
                }
            }
            sortFields[i] = new SortField(tokens[0], SortField.STRING, reverse);
        }
        return new Sort(sortFields);
    }

    public NodeInfo next() throws XPathException {
        long t = System.nanoTime();
        int startPosition = position;
        try {
            int docID = docIter.nextDoc();
            // LoggerFactory.getLogger(ResultIterator.class).trace("GET {} {}", docID, query);
            if (docID == Scorer.NO_MORE_DOCS) {
                position = -1;
                current = null;
            } else {
                long t1 = System.nanoTime();
                XdmItem doc = docCache.get(docID, searcher.getIndexReader());
                NodeInfo item = (NodeInfo) doc.getUnderlyingValue();
                // assert documents in order 
                assert (current == null || ((TinyDocumentImpl)item).getDocumentNumber() > ((TinyDocumentImpl)current).getDocumentNumber());
                current = item;
                ++position;
                if (stats != null) {
                    stats.retrievalTime += System.nanoTime() - t1;
                }
            }
        } catch (IOException e) {
            throw new XPathException(e);
        } finally {
            if (stats != null) {
                if (position >= 0) {
                    stats.docCount += (position - startPosition);
                }
                stats.totalTime += System.nanoTime() - t;
            }
        }
        return current;
    }

    public NodeInfo current() {
        return current;
    }

    public int position() {
        return position;
    }

    public void close() {
        // Saxon doesn't call this reliably
    }

    public SequenceIterator<NodeInfo> getAnother() throws XPathException {
        try {
            return new SearchResultIterator (searcher, docCache, stats, query, sortCriteria);
        } catch (IOException e) {
            throw new XPathException (e);
        }
    }

    public int getProperties() {
        return 0;
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
