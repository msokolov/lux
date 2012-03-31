package lux;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Iterator;

import javax.xml.stream.XMLStreamException;

import lux.api.Evaluator;
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

public abstract class SearchBase {

    private static Directory dir;
    protected static IndexSearcher searcher;
    private static final Version luceneVersion = Version.LUCENE_34;
    protected static int totalDocs;
    protected static int QUERY_EXACT = 0x00000001;
    protected static int QUERY_FILTER_FREE = 0x00000002;
    protected static int QUERY_MINIMAL = 0x00000004;

    @BeforeClass
    public static void setUp() throws Exception {
        // create an in-memory Lucene index, index some content
        dir = new RAMDirectory();
        indexAllElements ("lux/hamlet.xml");
        searcher = new IndexSearcher(dir);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        searcher.close();
        dir.close();
    }

    protected static HashMap<String,Integer> elementCounts = new HashMap<String,Integer>();
    
    /**
     * index and store all elements of an xml document found on the classpath
     * 
     * @param filename the pathname of the document to index
     * @throws XMLStreamException
     * @throws IOException
     */
    protected static void indexAllElements(String filename) throws XMLStreamException, IOException {
        indexAllElements(SearchTest.class.getClassLoader().getResourceAsStream(filename));
        System.out.println ("Indexed " + totalDocs + " documents from " + filename);
    }
    
    protected static void indexAllElements(InputStream in) throws XMLStreamException, IOException {
        IndexWriter indexWriter = new IndexWriter(dir, new IndexWriterConfig(luceneVersion, new StandardAnalyzer(luceneVersion)));
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
        indexWriter.close();
    }

    public abstract Evaluator getEvaluator();

    protected static void indexDocument(IndexWriter indexWriter, String xml, XmlIndexer indexer) throws XMLStreamException, CorruptIndexException, IOException {
        indexer.index(new StringReader(xml));
        Document doc = new Document();
        for (String fieldName : indexer.getFieldNames()) {
            for (Object value : indexer.getFieldValues(fieldName)) {
                // TODO: handle other primitive value types; put indexing hints
                // in the indexer
                doc.add(new Field(fieldName, value.toString(), Store.NO, indexer.isTokens(fieldName) ? Index.ANALYZED : Index.NOT_ANALYZED));
            }
        }
        doc.add(new Field("xml_text", xml, Store.YES, Index.NOT_ANALYZED));
        indexWriter.addDocument(doc);
    }

}