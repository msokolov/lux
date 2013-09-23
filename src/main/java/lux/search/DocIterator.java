package lux.search;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;

/**
 * Used to return results that are sorted in document order, or unordered.
 */
public class DocIterator extends DocIdSetIterator {
      
    private final LuxSearcher luxSearcher;
    private final Weight weight;
    private final boolean ordered;
    private int nextReader;
    private int docID;
    private Scorer scorer;
    private List<AtomicReaderContext> leaves;
    private AtomicReaderContext leaf;
      
    /**
     * @param query the lucene query whose results will be iterated
     * @param ordered whether the docs must be scored in order
     * @param luxSearcher TODO
     * @throws IOException
     */
    DocIterator (LuxSearcher luxSearcher, Query query, boolean ordered) throws IOException {
        this.luxSearcher = luxSearcher;
        weight = this.luxSearcher.createNormalizedWeight(query);
        leaves = this.luxSearcher.getIndexReader().leaves();
        this.ordered = ordered;
        nextReader = 0;
        docID = -1;
        advanceScorer();
    }

    private void advanceScorer () throws IOException {
        while (nextReader < leaves.size()) {
            leaf = leaves.get(nextReader++);
            scorer = weight.scorer(leaf, ordered, false, leaf.reader().getLiveDocs()); // NB: arg 3 (topScorer) was 'true' prior to 4.1 upgrade but incorrectly I think??
            if (scorer != null) {
                return;
            }
        }
        scorer = null;
    }
      
    @Override
    public int docID() {
        return docID;
    }

    /**
     * Note: this returns docIDs that must be interpreted relative to the current leaf reader:
     * these are not offset by the leaf.docBase.
     */
    @Override
    public int nextDoc() throws IOException {
        while (scorer != null) {
            docID = scorer.nextDoc();
            if (docID != NO_MORE_DOCS) {
                return docID;
            }
            advanceScorer();
        }
        return NO_MORE_DOCS;
    }

    @Override
    public int advance(int target) throws IOException {
        while (scorer != null) {
            docID = scorer.advance(target - leaf.docBase);
            if (docID != NO_MORE_DOCS) {
                return docID + leaf.docBase;
            }
            advanceScorer();
        }
        return NO_MORE_DOCS;
    }
      
    
    public AtomicReaderContext getCurrentReaderContext () {
        return leaf;
    }

    @Override
    public long cost() {
        return 0;
    }
 
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
