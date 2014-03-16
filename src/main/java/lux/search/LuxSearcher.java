package lux.search;

import java.io.IOException;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;

public class LuxSearcher extends IndexSearcher {

  // a reader managed by this exclusively
  private final IndexReader indexReader;
  
  private final IndexSearcher wrappedSearcher;
    
  /**
   * creates a Lux searcher that searches the given {@link Directory}.
   * @param dir the Directory containing the index to search
   * @throws IOException if the Directory cannot be opened
   */
  public LuxSearcher (Directory dir) throws IOException {
    super (DirectoryReader.open(dir));
    indexReader = getIndexReader();
    wrappedSearcher = null;
  }

  /**
   * creates a Lux searcher based on an existing Lucene IndexSearcher
   * @param searcher the underlying {@link IndexSearcher}
   */
  public LuxSearcher (IndexSearcher searcher) {
      super (searcher.getIndexReader());
      indexReader = null;
      wrappedSearcher = searcher;
  }
  
  /**
   * The reader will be managed by this LuxSearcher: when the searcher is closed, it will close the
   * underlying reader, unlike in the other constructors, where the reader is expected to be managed externally.
   * @param reader
   */
  public LuxSearcher (IndexReader reader) {
      super (reader);
      this.indexReader = reader;
      wrappedSearcher = null;
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
  public DocIterator search (Query query) throws IOException {
      return new DocIterator (this, query, false);
  }
  
  @Override
  public TopFieldDocs search (Weight weight, int size, Sort sort, boolean b1, boolean b2) throws IOException {
      return super.search(weight,  size, sort, b1, b2);
  }
  
  /**
   * @param query the Lucene query
   * @param sort the sort criteria
   * @return the results of the query as a Lucene DocIdSetIterator, ordered using the sort criterion. 
   * Results are returned in batches, so deep paging is possible, but expensive.
   * @throws IOException
   */
  public TopDocsIterator search (Query query, Sort sort) throws IOException {
      return new TopDocsIterator (this, query, sort);
  }

  /**
   * @param query the Lucene query
   * @return the results of the query as a Lucene DocIdSetIterator in docID order
   * @throws IOException
   */
  public DocIterator searchOrdered (Query query) throws IOException {
      return new DocIterator (this, query, true);
  }
  
  /**
   * @return the searcher from which this was created, or null.
   */
  public IndexSearcher getWrappedSearcher() {
      return wrappedSearcher;
  }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
