package lux;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import lux.index.XmlIndexer;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.jaxen.ContextSupport;
import org.jaxen.JaxenException;
import org.jdom.Element;
import org.jdom.filter.ElementFilter;
import org.jdom.output.XMLOutputter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class SearchTest {
    
    private static Directory dir;
    private static IndexSearcher searcher;
    private static final Version luceneVersion = Version.LUCENE_34;
    private static int totalDocs;
    
    @BeforeClass public static void setUp () throws Exception {
        // create an in-memory Lucene index, index some content
        dir = new RAMDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(luceneVersion, new StandardAnalyzer(luceneVersion)));
        indexHamlet(writer);
        writer.close();
        searcher = new IndexSearcher(dir);
    }
    
    @AfterClass public static void tearDown () throws Exception {
        searcher.close();
        dir.close();
    }
    
    private static HashMap<String,Integer> elementCounts = new HashMap<String,Integer>();
    
    /*
     * Index the entire text of Hamlet as one document, and each speech as a separate document.
     */
    private static void indexHamlet (IndexWriter indexWriter) throws XMLStreamException, IOException {
        InputStream in = SearchTest.class.getClassLoader().getResourceAsStream("lux/hamlet.xml");
        String hamlet = IOUtils.toString(in);
        XmlIndexer indexer = new XmlIndexer (XmlIndexer.BUILD_JDOM);
        indexDocument(indexWriter, hamlet, indexer);
        XMLOutputter outputter = new XMLOutputter();
        // index all descendants
        totalDocs = 1;
        Iterator<?> iter = indexer.getJDOM().getDescendants(new ElementFilter());
        iter.next(); // skip the root element, we already indexed it
        while (iter.hasNext()) {
            Element e = (Element) iter.next();
            Integer count = elementCounts.get (e.getName());
            if (count == null) {
                elementCounts.put (e.getName(), 1);
            } else {
                elementCounts.put (e.getName(), count + 1);
            }
            ++totalDocs;
            String speech = outputter.outputString(e);
            indexDocument (indexWriter, speech, indexer);
        } 
        indexWriter.commit();
        System.out.println ("Indexed " + totalDocs + " documents from Hamlet");
    }

    private static void indexDocument(IndexWriter indexWriter, String xml, XmlIndexer indexer) throws XMLStreamException,
            CorruptIndexException, IOException {
        indexer.index(new StringReader (xml));        
        Document doc = new Document();
        for (String fieldName : indexer.getFieldNames()) {
            for (Object value : indexer.getFieldValues(fieldName)) {
                // TODO: handle other primitive value types; put indexing hints in the indexer
                doc.add(new Field(fieldName, value.toString(), Store.NO, Index.NOT_ANALYZED));
            }
        }
        doc.add (new Field ("xml_text", xml, Store.YES, Index.NOT_ANALYZED));
        indexWriter.addDocument(doc);
    }
    
    @Test 
    public void testTimer () throws Exception {
        // 6.2 seconds + -> 6636*20/6.2 = 21,000 results per/sec!
        for (int i = 0; i < 20; i++) {
            testSearchAllDocs();
        }
    }
    
    @Test public void testTimeNoParse () throws Exception {
        // 0.4 sec!! -> 15X speedup
        // This is an upper bound on what we could expect from using non-parsed XML storage
        // saving us a parse-and-create OM step inside the query evaluator
        // For Solr the speedup could be even better b/c there we have to serialize the results
        // It doesn't count XPath evaluation, which for more complex expressions would dominate
        for (int i = 0; i < 20; i++) {
            LuXPathBasic xpath = new LuXPathBasic ("/");
            xpath.dontParse = true;
            List<?> results = (List<?>) xpath.evaluate(new QueryContext(new ContextSupport(), searcher));
            assertEquals (totalDocs, results.size());
        }
    }
    
    @Test
    public void testSearchAllDocs() throws Exception {
        List<?> results = searchx("/");
        assertEquals (totalDocs, results.size());
    }

    private List<?> searchx(String query) throws JaxenException {
        LuXPath xpath = new LuXPathBasic (query);
        return (List<?>) xpath.evaluate(new QueryContext(new ContextSupport(), searcher));
    }
    
    @Test
    public void testSearchAct() throws Exception {
        List<?> results = searchx ("/ACT");
        assertEquals (elementCounts.get("ACT") + 0, results.size());
    }
    
    @Test
    public void testSearchActScene() throws Exception {
        List<?> results = searchx("/ACT/SCENE");
        assertEquals (elementCounts.get("SCENE") + 0, results.size());
    }
    
    @Test
    public void testSearchAllScenes() throws Exception {
        List<?> results = searchx("//SCENE");
        // every SCENE, in its ACT and in the PLAY
        assertEquals (elementCounts.get("SCENE") * 3, results.size());
    }
    
    @Test
    public void testSearchAllSceneDocs() throws Exception {
        List<?> results = searchx("(/)[.//SCENE]");
        // every SCENE, in its ACT and in the PLAY
        assertEquals (elementCounts.get("SCENE") + elementCounts.get("ACT") + 1, results.size());
    }
}
