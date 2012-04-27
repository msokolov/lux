package lux.lucene;

import java.io.IOException;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;

public class LuxSearcher extends IndexSearcher {

  public LuxSearcher (Directory dir) throws IOException {
    super (dir);
  }

  public DocIdSetIterator search (Query query) throws IOException {
      return new DocIterator (query, false);
  }

  public DocIdSetIterator searchOrdered (Query query) throws IOException {
      return new DocIterator (query, true);
  }
  
  class DocIterator extends DocIdSetIterator {
      
      private final Weight weight;
      private final boolean ordered;
      private int nextReader;
      private int docID;
      private Scorer scorer;
      
      /**
       * @param query the lucene query whose results will be iterated
       * @param ordered whether the docs must be scored in order
       * @throws IOException
       */
      DocIterator (Query query, boolean ordered) throws IOException {
          weight = createNormalizedWeight(query);
          this.ordered = ordered;
          nextReader = 0;
          docID = -1;
          advanceScorer();
      }

      private void advanceScorer () throws IOException {
          while (nextReader < subReaders.length) {
              scorer = weight.scorer(subReaders[nextReader++], ordered, true);
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
                return docID;
            }
            advanceScorer();
        }
        return NO_MORE_DOCS;
    }

    @Override
    public int advance(int target) throws IOException {
        while (scorer != null) {
            docID = scorer.advance(target);
            if (docID != NO_MORE_DOCS) {
                return docID;
            }
            advanceScorer();
        }
        return NO_MORE_DOCS;
    }
      
  }
  
}