package lux;

import java.io.IOException;

import lux.exception.LuxException;
import lux.search.LuxSearcher;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.trans.XPathException;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;

/**
 * Executes a Lucene search and provides the results as a Saxon {@link SequenceIterator}.
 * Sort criteria are translated into Lucene SortFields: relevance score, intrinsic document order, and
 * field-value orderings are supported.
 */
public class SearchResultIterator implements SequenceIterator<NodeInfo> {
    
    private final DocIdSetIterator docIter;
    private final Query query;
    private final QueryStats stats;
    private final LuxSearcher searcher;
    private final String sortCriteria;
    private CachingDocReader docCache;
    private NodeInfo current = null;
    private int position = 0;
    
    /**
     * Executes a Lucene search.
     * @param eval provides the link to the index via its {@link IndexSearcher}.
     * @param query the query to execute
     * @param sortCriteria sort criteria, formatted as a comma-separated list of sort field names;
     * each name may be followed by ascending|descending.  The sort criteria are Lucene field names, or
     * may be the special name "lux:score", which selects relevance score ranking. If null, results
     * are returned in intrinsic document order.
     * @throws IOException
     */
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
            Sort sort = makeSortFromCriteria(sortCriteria);
            docIter = searcher.search(query, sort);
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
                if (tokens[1].equals("descending")) {
                    reverse = true;
                } else if (!tokens[1].equals("ascending")) {
                    throw new LuxException ("Invalid sort key keyword: " + tokens[2] + " in: " + sortCriteria);
                }
                if (tokens.length > 2) {
                    throw new LuxException ("Invalid sort key: " + sortCriteria);
                }
            }
            // TODO: use or copy from org.apache.solr.Sorting to implement missing least/greatest
            String field = tokens[0];
            if (field.equals("lux:score")) {
                if (! reverse) {
                    throw new LuxException ("Not countenanced: attempt to sort by irrelevance");
                }
                sortFields[i] = SortField.FIELD_SCORE;
            } else {
                sortFields[i] = new SortField(field, SortField.STRING, reverse);
            }
        }
        return new Sort(sortFields);
    }

    /**
     * @return the next result.  Returns null when there are no more results.
     * Calling this function after null has been returned may result
     * in an error.
     * @throws XPathException if there is an error while searching
     */
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
                // assert documents in order : Note this is no longer accurate now that we have implemented "order by"
                // assert (current == null || ((TinyDocumentImpl)item).getDocumentNumber() > ((TinyDocumentImpl)current).getDocumentNumber());
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

    /**
     * @return the current result.  This is the last result returned by next(), and will be null if there
     * are no more results.
     */
    public NodeInfo current() {
        return current;
    }

    /**
     * @return the (0-based) index of the next result: this will be 0 before any calls to next(), and -1 after the last
     * result has been retrieved.
     */
    public int position() {
        return position;
    }

    /**
     * does nothing
     */
    public void close() {
        // Saxon doesn't call this reliably
    }

    /**
     * @return a clone of this iterator, reset to the initial position.
     */
    public SequenceIterator<NodeInfo> getAnother() throws XPathException {
        try {
            return new SearchResultIterator (searcher, docCache, stats, query, sortCriteria);
        } catch (IOException e) {
            throw new XPathException (e);
        }
    }

    /**
     *  This iterator has no special properties
     * @return 0
     */
    public int getProperties() {
        return 0;
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
