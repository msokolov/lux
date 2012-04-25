package lux.lucene;

import java.io.IOException;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.store.Directory;

public class LuxSearcher extends IndexSearcher {

  public LuxSearcher (Directory dir) throws IOException {
    super (dir);
  }

  public Scorer search (Query query) throws IOException {
      return createNormalizedWeight(query).scorer(getIndexReader(), true, false);
  }
  
}