package lux.search;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;

public class LuxSearcher extends IndexSearcher {

  // a reader managed by this exclusively
  private final IndexReader indexReader;
    
  /**
   * creates a Lux searcher that searches the given {@link Directory}.
   * @param dir the Directory containing the index to search
   * @throws IOException if the Directory cannot be opened
   */
  public LuxSearcher (Directory dir) throws IOException {
    super (DirectoryReader.open(dir));
    indexReader = getIndexReader(); 
  }

  /**
   * creates a Lux searcher based on an existing Lucene IndexSearcher
   * @param searcher the underlying {@link IndexSearcher}
   */
  public LuxSearcher (IndexSearcher searcher) {
      super (searcher.getIndexReader());
      indexReader = null;
  }
  
  /**
   * The reader will be managed by this LuxSearcher: when the searcher is closed, it will close the
   * underlying reader, unlike in the other constructors, where the reader is expected to be managed externally.
   * @param reader
   */
  public LuxSearcher (IndexReader reader) {
      super (reader);
      this.indexReader = reader;
  }
  
  public void close () throws IOException {
      if (indexReader != null) {
          indexReader.close();
      }
  }

  /**
   * @param query the Lucene query
   * @return the unordered results of the query as a Lucene DocIdSetIterator.  Unordered means the order
   * is not predictable and may change with subsequent calls. 
   * @throws IOException
   */
  public DocIdSetIterator search (Query query) throws IOException {
      return new DocIterator (query, false);
  }
  
  /**
   * @param query the Lucene query
   * @param sort the sort criteria
   * @return the results of the query as a Lucene DocIdSetIterator, ordered using the sort criterion. 
   * Results are returned in batches, so deep paging is possible, but expensive.
   * @throws IOException
   */
  public DocIdSetIterator search (Query query, Sort sort) throws IOException {
      return new TopDocsIterator (query, sort);
  }

  /**
   * @param query the Lucene query
   * @return the results of the query as a Lucene DocIdSetIterator in docID order
   * @throws IOException
   */
  public DocIdSetIterator searchOrdered (Query query) throws IOException {
      return new DocIterator (query, true);
  }
  
  class DocIterator extends DocIdSetIterator {
      
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
       * @throws IOException
       */
      DocIterator (Query query, boolean ordered) throws IOException {
          weight = createNormalizedWeight(query);
          leaves = getIndexReader().leaves();
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

    @Override
    public int nextDoc() throws IOException {
        while (scorer != null) {
            docID = scorer.nextDoc();
            if (docID != NO_MORE_DOCS) {
                return docID + leaf.docBase;
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
      
  }
  
  class TopDocsIterator extends DocIdSetIterator {
      
      private final Query query;
      private final Sort sort;
      private int docID = -1;
      private int iDocNext = 0;
      // private int iDocBase = 0;
      private TopDocs topDocs;
      private static final int BATCH_SIZE = 200;
      
      TopDocsIterator (Query query, Sort sort) throws IOException {
          this.query = query;
          this.sort = sort;
          topDocs = search(createNormalizedWeight(query), BATCH_SIZE, sort, false, false);
      }

      @Override
      public int docID() {
          return docID;
      }

      @Override
      public int nextDoc() throws IOException {
          if (iDocNext < topDocs.scoreDocs.length) {
              docID = topDocs.scoreDocs[iDocNext++].doc;
          }
          else if (iDocNext < topDocs.totalHits) {
              // load a larger batch of docs
              // TODO: a better implementation would remember the previous endpoint
              // by value (total ordering=sortkey,docID) and skip over the first set of docs,
              // storing only the next batch - we could possibly add a term to the query expressing
              // this?
              // iDocBase = iDocNext;
              topDocs = search(createNormalizedWeight(query), iDocNext + BATCH_SIZE, sort, false, false);
          }
          else {
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

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
