package lux;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import lux.index.XmlField;
import lux.index.XmlIndexer;
import lux.lucene.LuxSearcher;

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.RAMDirectory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * measures space and time for different indexing options
 */
public class IndexTest {
    
    private RAMDirectory dir;

    @Test
    public void testIndexQNames() throws Exception {
        buildIndex ("qnames", XmlIndexer.INDEX_QNAMES | XmlIndexer.BUILD_JDOM);
        assertTotalDocs ();
    }

    @Test
    public void testIndexPaths() throws Exception {
        XmlIndexer indexer = buildIndex ("paths", XmlIndexer.INDEX_PATHS | XmlIndexer.BUILD_JDOM);
        assertTotalDocs ();
        printAllTerms();
        indexer.index(getClass().getResourceAsStream("hamlet.xml"));
        for (Object s : indexer.getFieldValues(XmlField.PATH)) {
            System.out.println (s);
        }
    }

    @Test
    public void testIndexQNamesAndPaths() throws Exception {
        buildIndex ("qnames and paths", XmlIndexer.INDEX_QNAMES | XmlIndexer.INDEX_PATHS | XmlIndexer.BUILD_JDOM);
        assertTotalDocs ();
    }

    @Test
    public void testStoreDocuments() throws Exception {
        buildIndex ("xml storage", XmlIndexer.STORE_XML| XmlIndexer.BUILD_JDOM);
        assertTotalDocs ();
    }
    
    @Before
    public void setup() {
        dir = new RAMDirectory();
    }
    
    @After
    public void cleanup() {
        dir.close();
    }
    
    private XmlIndexer buildIndex (String desc, int options) throws XMLStreamException, IOException {
        XmlIndexer indexer = new XmlIndexer (options);
        long t0 = System.currentTimeMillis();
        SearchBase.indexAllElements (indexer, dir, "lux/hamlet.xml");
        System.out.println 
             (String.format("indexed %s in %d ms %d bytes", desc, 
                     (System.currentTimeMillis()-t0), dir.sizeInBytes()));
        return indexer;
    }

    private void printAllTerms() throws CorruptIndexException, IOException {
        IndexReader reader = IndexReader.open(dir);
        TermEnum terms = reader.terms();
        while (terms.next()) {
            System.out.println (terms.term());
        }
        reader.close();
    }
        
    private void assertTotalDocs() throws IOException {
        LuxSearcher searcher = new LuxSearcher(dir);
        DocIdSetIterator results = searcher.search(new MatchAllDocsQuery());
        int count = 0;
        while (results.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            ++count;
        }
        assertEquals (6636, count);
        /*
        */
        searcher.close();
    }
    
}
