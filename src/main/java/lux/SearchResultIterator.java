package lux;

import java.io.IOException;

import lux.exception.LuxException;
import lux.search.LuxSearcher;
import lux.solr.MissingStringLastComparatorSource;
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
    private final int start;
    private CachingDocReader docCache;
    private NodeInfo current = null;
    private int position = 0;
    public static final MissingStringLastComparatorSource MISSING_LAST = new MissingStringLastComparatorSource();
    
    /**
     * Executes a Lucene search.
     * @param eval provides the link to the index via its {@link IndexSearcher}.
     * @param query the query to execute
     * @param sortCriteria sort criteria, formatted as a comma-separated list of sort field names;
     * each name may be followed by ascending|descending, and/or by "empty greatest"|"empty least".
     * The default is "ascending empty least".
     * The sort criteria are Lucene field names, or may be the special name "lux:score", which selects 
     * relevance score ranking, which is always sorted in descending order: modifiers on relevance orders are ignored. 
     * If no ordering is provided, results are returned in intrinsic document order (ie ordered by document ID).
     * @param start 
     * @throws IOException
     */
    public SearchResultIterator (Evaluator eval, Query query, String sortCriteria, int start) throws IOException {
        this (eval.getSearcher(), eval.getDocReader(), eval.getQueryStats(), query, sortCriteria, start);
    }
    
    protected SearchResultIterator (LuxSearcher searcher, CachingDocReader docReader, QueryStats stats, Query query, String sortCriteria, int start) throws IOException {
        this.query = query;
        this.searcher = searcher;
        this.docCache = docReader;
        this.stats = stats;
        this.sortCriteria = sortCriteria;
        this.start = start;
        if (stats != null) {
            stats.query = query.toString();
        }
        if (searcher == null) {
            throw new LuxException("Attempted to search using an Evaluator that has no searcher");
        }
        if (sortCriteria != null) {
            Sort sort = makeSortFromCriteria();
            docIter = searcher.search(query, sort);
        } else {
            docIter = searcher.searchOrdered(query);
        }
        if (start > 1) {
            advanceTo (start);
        }
    }

    private Sort makeSortFromCriteria() {
        String[] fields = sortCriteria.split(",");
        SortField[] sortFields = new SortField [fields.length];
        int type = SortField.STRING;
        for (int i = 0; i < fields.length; i++) {
            String [] tokens = fields[i].split("\\s+");
            String field = tokens[0];
            Boolean reverse = null;
            Boolean emptyGreatest = null;
            for (int j = 1; j < tokens.length; j++) {
                if (tokens[j].equals("descending")) {
                    reverse = setBooleanOnce (reverse, true, sortCriteria);
                } else if (tokens[j].equals("ascending")) {
                    reverse = setBooleanOnce (reverse, false, sortCriteria);
                } else if (tokens[j].equals("empty")) {
                    if (j == tokens.length-1) {
                        throw new LuxException ("missing keyword after 'empty' in sort criterion: " + sortCriteria);
                    }
                    j = j + 1;
                    if (tokens[j].equals("least")) {
                        emptyGreatest = setBooleanOnce(emptyGreatest, false, sortCriteria);
                    } 
                    else if (tokens[j].equals("greatest")) {
                        emptyGreatest = setBooleanOnce(emptyGreatest, true, sortCriteria);
                    }
                    else {
                        throw new LuxException ("missing or invalid keyword after 'empty' in sort criterion: " + sortCriteria);
                    }
                } else if (tokens[j].equals("int")) {
                    type = SortField.INT;
                } else if (tokens[j].equals("long")) {
                    type = SortField.LONG;
                } else {
                    throw new LuxException ("invalid keyword '" + tokens[j] + "' in sort criterion: " + sortCriteria);
                }
            }
            if (field.equals("lux:score")) {
                if (reverse == Boolean.FALSE) {
                    throw new LuxException ("Not countenanced: attempt to sort by irrelevance");
                }
                sortFields[i] = SortField.FIELD_SCORE;
            } else {
                if (emptyGreatest == Boolean.TRUE) {
                    sortFields[i] = new SortField(field, MISSING_LAST, reverse == Boolean.TRUE);
                } else {
                    sortFields[i] = new SortField(field, type, reverse == Boolean.TRUE);
                }
            }
        }
        return new Sort(sortFields);
    }

    private final Boolean setBooleanOnce (Boolean current, boolean value, String sortCriteria) {
        if (current != null) {
            throw new LuxException ("Too many sort ordering keywords in: " + sortCriteria);
        }
        return value;
    }
    
    /**
     * @return the next result.  Returns null when there are no more results.
     * Calling this function after null has been returned may result
     * in an error.
     * @throws XPathException if there is an error while searching
     */
    @Override
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
     * advance the iterator to (just before) the given (1-based) position.  Sets current to null: next() must be called
     * after this method in order to retrieve the result at the position.
     * @param startPosition
     * @throws IOException 
     */
    protected void advanceTo (int startPosition) throws IOException {
        long t = System.nanoTime();
        int start0 = startPosition-1;
        try {
            int docID=0;
            current = null;
            while (position < start0) {
                docID = docIter.nextDoc();
                if (docID == Scorer.NO_MORE_DOCS) {
                    position = -1;
                    break;
                }
                ++position;
            }
            if (stats != null) {
            }
        } finally {
            if (stats != null) {
                long t1 = System.nanoTime();
                stats.retrievalTime += t1 - t;
                stats.totalTime += t1 - t;
            }
        }
    }

    /**
     * @return the current result.  This is the last result returned by next(), and will be null if there
     * are no more results.
     */
    @Override
    public NodeInfo current() {
        return current;
    }

    /**
     * @return the (0-based) index of the next result: this will be 0 before any calls to next(), and -1 after the last
     * result has been retrieved.
     */
    @Override
    public int position() {
        return position;
    }

    /**
     * does nothing
     */
    @Override
    public void close() {
        // Saxon doesn't call this reliably
    }

    /**
     * @return a clone of this iterator, reset to the initial position.
     */
    @Override
    public SequenceIterator<NodeInfo> getAnother() throws XPathException {
        try {
            return new SearchResultIterator (searcher, docCache, stats, query, sortCriteria, start);
        } catch (IOException e) {
            throw new XPathException (e);
        }
    }

    /**
     *  This iterator has no special properties
     * @return 0
     */
    @Override
    public int getProperties() {
        return 0;
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
