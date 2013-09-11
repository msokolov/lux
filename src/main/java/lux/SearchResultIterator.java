package lux;

import java.io.IOException;

import lux.exception.LuxException;
import lux.search.DocIterator;
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

/**
 * Executes a Lucene search and provides the results as a Saxon {@link SequenceIterator}.
 * Sort criteria are translated into Lucene SortFields: relevance score, intrinsic document order, and
 * field-value orderings are supported.
 */
public class SearchResultIterator extends SearchIteratorBase {

    private final Query query;
    private final DocIdSetIterator docIter;
    private final LuxSearcher searcher;
    private CachingDocReader docCache;
    
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
     * @param start1 the 1-based starting position of the iteration
     * @throws IOException
     */
    public SearchResultIterator (Evaluator eval, Query query, String sortCriteria, int start1) throws IOException {
        super (eval, sortCriteria, start1);
        this.query = query;
        if (stats != null) {
            stats.query = query.toString();
        }
        this.searcher = eval.getSearcher();
        this.docCache = eval.getDocReader();
        if (searcher == null) {
            throw new LuxException("Attempted to search using an Evaluator that has no searcher");
        }
        if (sortCriteria != null) {
            Sort sort = makeSortFromCriteria();
            docIter = searcher.search(query, sort);
        } else {
            docIter = searcher.searchOrdered(query);
        }
        if (start > 0) {
            advanceTo (start1);
        }
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
                XdmItem doc;
                if (sortCriteria == null) {
                    // in this case, we are iterating over the readers in order, so we can make the retrieval a bit
                    // faster by going directly to the appropriate leaf reader
                    doc = docCache.get(docID, ((DocIterator) docIter).getCurrentReaderContext());
                } else {
                    // FIXME: use relative docID and leaf reader here  so we can avoid a binary search to find the 
                    // correct reader.  In fact the LuxSearcher *already* has the correct leaf reader - we just need
                    // to make it available here - probably via the docIter
                    doc = docCache.get(docID, searcher.getIndexReader());
                }
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
     * @return a clone of this iterator, reset to the initial position.
     */
    @Override
    public SequenceIterator<NodeInfo> getAnother() throws XPathException {
        try {
            return new SearchResultIterator (eval, query, sortCriteria, start + 1);
        } catch (IOException e) {
            throw new XPathException (e);
        }
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
