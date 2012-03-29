package lux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import lux.api.Evaluator;
import lux.api.LuxException;
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
import org.jdom.Element;
import org.jdom.filter.ElementFilter;
import org.jdom.output.XMLOutputter;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public abstract class SearchTest {
    
    private static Directory dir;
    protected static IndexSearcher searcher;
    private static final Version luceneVersion = Version.LUCENE_34;
    protected static int totalDocs;
    // This means there is exactly one result returned per document matching the query,
    // making the query more easily pageable using solr/lucene paging, and making the search count accurate
    private static int QUERY_EXACT = 0x00000001;
    // This means "filtering" is not required; the query returns an aggregate, like count
    // or it returns documents or uris, or in general anything that can be computed without
    // examining the contents of the document using xpath.
    private static int QUERY_FILTER_FREE = 0x00000002;
    // The query retrieves only documents that actually produce results - no smaller set of 
    // documents would suffice.  This is a necessary, but not sufficient, condition for EXACT.
    // It's not clear if there are any benefits to just being minimal, though? 
    private static int QUERY_MINIMAL = 0x00000004;
    
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
    
    public abstract Evaluator getEvaluator();
    
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
        elementCounts.clear();
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
    public void testIndexSetup() throws Exception {
        // This test serves only to separate the timing of the initialization phase
        // from the timings of the subsequent tests.  JUnit runners tend to 
        // report the indexing time as part of the time of the firs test.
    }
    
    @Test
    public void testSearchAllDocs() throws Exception {
        List<?> results = assertSearch("/", QUERY_EXACT);
        assertEquals (totalDocs, results.size());
    }
    
    @Test
    public void testCountAllDocs () throws Exception {        
        List<?> results = assertSearch ("count(/)", QUERY_FILTER_FREE);
        assertEquals (Double.valueOf(totalDocs), (Double)results.get(0));
    }
    
    private List<?> assertSearch(String query) throws LuxException {
        return assertSearch (query, 0);
    }
    
    private List<?> assertSearch(String query, int props) throws LuxException {
        Evaluator eval = getEvaluator();
        Object result = eval.evaluate(eval.compile(query));
        List<?> results;
        if (result == null) {
            results = Collections.EMPTY_LIST;
        }
        else if (result instanceof List) {
            results = (List<?>) result;
        } else {
            results = Collections.singletonList(result);
        }
        if ((props & QUERY_EXACT) != 0) {
            assertEquals (results.size(), eval.getQueryStats().docCount);
        }
        if ((props & QUERY_MINIMAL) != 0) {
            // this is not the same as minimal, but is implied by it:
            assertTrue (results.size() >= eval.getQueryStats().docCount);
            // in addition we'd need to show that every document produced at least one result
        }
        if ((props & QUERY_FILTER_FREE) != 0) {
            // if we spend < 1% of our time in the collector, we didn't do a lot of xquery evaluation
            assertTrue ((eval.getQueryStats().collectionTime + 1) / (eval.getQueryStats().totalTime + 1.0) < 0.01);
        }
        return results;
    }

    @Test
    public void testSearchAct() throws Exception {
        List<?> results = assertSearch ("/ACT");
        assertEquals (elementCounts.get("ACT") + 0, results.size());
    }
    
    @Test
    public void testSearchActScene() throws Exception {
        List<?> results = assertSearch("/ACT/SCENE");
        assertEquals (elementCounts.get("SCENE") + 0, results.size());
    }
    
    @Test
    public void testSearchAllScenes() throws Exception {
        List<?> results = assertSearch("//SCENE");
        // every SCENE, in its ACT and in the PLAY
        assertEquals (elementCounts.get("SCENE") * 3, results.size());
    }
    
    @Test
    public void testSearchAllSceneDocs() throws Exception {
        List<?> results = assertSearch("(/)[.//SCENE]", QUERY_EXACT);
        // every SCENE, in its ACT and in the PLAY
        assertEquals (elementCounts.get("SCENE") + elementCounts.get("ACT") + 1, results.size());
    }
    
    @Test @Ignore
    public void testSearchAllSceneDocsRoot() throws Exception {
        // This syntax is not supported by XPath 1.0
        List<?> results = assertSearch(".//SCENE/fn:root()", QUERY_EXACT);
        // every SCENE, in its ACT and in the PLAY
        assertEquals (elementCounts.get("SCENE") + elementCounts.get("ACT") + 1, results.size());
    }
    
    @Test
    public void testSyntaxError () throws Exception {
        try {
            assertSearch ("hey bad boy");
            assertTrue ("expected LuxException to be thrown for syntax error", false);
        } catch (LuxException e) {
        }
    }
}
