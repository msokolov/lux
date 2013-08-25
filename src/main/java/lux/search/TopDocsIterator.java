package lux.search;

import java.io.IOException;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;

/**
 * Used to return results that are sorted by field value
 */
class TopDocsIterator extends DocIdSetIterator {

    private final LuxSearcher searcher;

    private final Query query;
    private final Sort sort;
    private int docID = -1;
    private int iDocNext = 0;
    // private int iDocBase = 0;
    private TopDocs topDocs;
    private static final int BATCH_SIZE = 200;

    TopDocsIterator(LuxSearcher luxSearcher, Query query, Sort sort) throws IOException {
        this.searcher = luxSearcher;
        this.query = query;
        this.sort = sort;
        topDocs = searcher.search(searcher.createNormalizedWeight(query), BATCH_SIZE, sort, false, false);
    }

    @Override
    public int docID() {
        return docID;
    }

    @Override
    public int nextDoc() throws IOException {
        if (iDocNext < topDocs.scoreDocs.length) {
            docID = topDocs.scoreDocs[iDocNext++].doc;
        } else if (iDocNext < topDocs.totalHits) {
            // load a larger batch of docs
            // TODO: a better implementation would remember the previous
            // endpoint
            // by value (total ordering=sortkey,docID) and skip over the first
            // set of docs,
            // storing only the next batch - we could possibly add a term to the
            // query expressing
            // this?
            // iDocBase = iDocNext;
            topDocs = this.searcher.search(
                    this.searcher.createNormalizedWeight(query), iDocNext
                            + BATCH_SIZE, sort, false, false);
        } else {
            // exhausted the entire result set
            docID = NO_MORE_DOCS;
        }
        return docID;
    }

    @Override
    public int advance(int target) throws IOException {
        // unimplemented - do we need this?
        return NO_MORE_DOCS;
    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
