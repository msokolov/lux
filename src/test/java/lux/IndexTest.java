package lux;

import static lux.index.IndexConfiguration.*;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import lux.index.FieldRole;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.index.field.FieldDefinition;
import lux.index.field.FieldDefinition.Type;
import lux.index.field.XPathField;
import lux.query.parser.XmlQueryParser;
import lux.search.LuxSearcher;
import net.sf.saxon.s9api.SaxonApiException;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.surround.parser.ParseException;
import org.apache.lucene.queryparser.surround.parser.QueryParser;
import org.apache.lucene.queryparser.surround.query.BasicQueryFactory;
import org.apache.lucene.queryparser.surround.query.SrndQuery;
import org.apache.lucene.queryparser.xml.ParserException;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.RAMDirectory;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Measures space and time for different indexing options, and validates
 * indexing results.
 * 
 * The timings are off; we'd need to run this repeatedly to avoid transient
 * startup effects which overwhelm the measurements for a single run.
 * 
 * But the space numbers (in bytes) should be valid (from Directory.sizeInBytes()):
 * XML storage: 3664896 (3.5M)
 * qnames = 3692544 - 3664896 = 27648 = 0.75%
 * paths = 3717120 - 3664896 = 52224 = 1.4%
 * 
 * After refactoring XmlField, etc:
 * XML storage: 2274304  why did this shrink so much?  We're now using serializer instead
 * of JDOM outputter - could this all be whitespace from indentation or something?
 * qnames: 2302976 - 2274304 = 28672 = 1.3%
 * paths: 2328576 - 2274304 = 54272 = 2.4%
 * path-occurrences = 122880 = 5.1%
 * path-values alone: 755712
 * path-values (w/docs): 2714624 - 2274304 = 19%
 * qname-values (as phrases): 2631680 - 2274304 = 357376 = 16%
 * qname-values (hashed into single tokens): 2542592 - 2274304 = 11.8%
 * qname-words w/o terminal tokens: 2683904 - 2274304 = 18%
 * qname-words + terminal tokens: 2786304 - 2274304 = 22%
 * full-text (with all nodes transparent) 3899392 - 2274304 = 1625088 = 71% (1940480 full text alone)
 * full-text (text only) 2673664 - 2274304 = 399360 = 18%
 * full-text (text plus all nodes opaque) 3068928 - 2274304 = 35%
 * 
 */
public class IndexTest {
    
    private static final boolean GATHER_TIMING = false;
    private RAMDirectory dir;

    @Test
    public void testIndexPaths() throws Exception {
        buildIndex ("paths and xml", INDEX_PATHS | STORE_DOCUMENT | BUILD_DOCUMENT);
        assertTotalDocs ();
    }

    private void reset() {
        dir.close();
        dir = new RAMDirectory();
    }
    
    @Test
    public void testIndexPathsOnly () throws Exception {
        IndexTestSupport indexTestSupport = buildIndex ("paths", INDEX_PATHS | BUILD_DOCUMENT);        
        assertTotalDocs ();
        // printAllTerms(indexTestSupport);
        assertPathQuery (indexTestSupport);
    }
    
    @Test
    public void testIndexQNames() throws Exception {
        buildIndex ("qnames and xml", INDEX_QNAMES | STORE_DOCUMENT | BUILD_DOCUMENT);
        assertTotalDocs ();
    }
    
    @Test
    public void testIndexQNamesOnly () throws Exception {
        buildIndex ("qnames", INDEX_QNAMES | BUILD_DOCUMENT);
        assertTotalDocs ();
    }

    @Test
    public void testIndexPathOccurOnly () throws Exception {
        // IndexTestSupport indexTestSupport = 
        buildIndex ("path-occurrences", INDEX_PATHS | INDEX_EACH_PATH | BUILD_DOCUMENT);
        // printAllTerms(indexTestSupport);
        assertTotalDocs ();
    }
    
    @Test
    public void testIndexFullText () throws Exception {
        buildIndex ("full-text", INDEX_FULLTEXT | STORE_DOCUMENT |BUILD_DOCUMENT);        
        assertTotalDocs ();
        // printAllTerms(indexTestSupport);
    }

    @Test
    public void testIndexFullTextOnly () throws Exception {
        //IndexTestSupport indexTestSupport = 
        buildIndex ("full-text-only", INDEX_FULLTEXT);        
        assertTotalDocs ();
        //printAllTerms(indexTestSupport);
    }

    private void assertPathQuery(IndexTestSupport indexTestSupport) throws ParseException, IOException {
        SrndQuery q = new QueryParser ().parse2("w(w({},\"ACT\"),\"SCENE\")");
        Query q2 = q.makeLuceneQueryFieldNoBoost(indexTestSupport.indexer.getConfiguration().getFieldName(FieldRole.PATH),  new BasicQueryFactory());
        DocIdSetIterator iter = indexTestSupport.searcher.search(q2);
        int count = 0;
        while (iter.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            ++ count;
        }
        assertEquals (5, count);
    }
    
    private void assertFullTextQuery(IndexTestSupport indexTestSupport, String qName, String term, int expectedCount) throws IOException, ParserException {
        LuxSearcher searcher = indexTestSupport.searcher;
        XmlIndexer indexer = indexTestSupport.indexer;
        IndexConfiguration config = indexer.getConfiguration();
        FieldDefinition field = config.getField(FieldRole.ELEMENT_TEXT);
        Query q = new XmlQueryParser(field.getName(), field.getAnalyzer()).parse
                (new ByteArrayInputStream(("<QNameTextQuery fieldName=\"" +
                        config.getFieldName(FieldRole.ELEMENT_TEXT) + "\" qName=\"" +
                		qName + "\">" + term +
                				"</QNameTextQuery>").getBytes()));
        DocIdSetIterator iter = searcher.search(q);
        int count = 0;
        while (iter.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            ++ count;
        }
        assertEquals (expectedCount, count);
    }
    
    private void assertXPathIntField (IndexTestSupport indexTestSupport) throws ParseException, IOException {
        Query q = NumericRangeQuery.newIntRange("nodecount", 6000, 20000, true, true);
        DocIdSetIterator iter = indexTestSupport.searcher.search(q);
        int count = 0;
        while (iter.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            ++ count;
        }
        assertEquals (1, count);
    }
    
    private void assertXPathStringField (int expectedCount, String field, String term, IndexTestSupport indexTestSupport) throws ParseException, IOException {
        Query q = new TermQuery (new Term (field, term));
        DocIdSetIterator iter = indexTestSupport.searcher.search(q);
        int count = 0;
        while (iter.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            ++ count;
        }
        assertEquals ("Wrong number of matches for " + q.toString(), expectedCount, count);
    }

    @Test
    public void testIndexFullTextOneDoc() throws Exception {
        XmlIndexer indexer = new XmlIndexer (INDEX_FULLTEXT);
        IndexWriter indexWriter = indexer.newIndexWriter(dir);
        indexer.indexDocument(indexWriter, "/lux/reader-test.xml", 
                getClass().getClassLoader().getResourceAsStream("lux/reader-test.xml"));
        indexWriter.close();
        IndexTestSupport.printAllTerms(dir, indexer);
        /*
            IndexTestSupport indexTestSupport = new IndexTestSupport ("lux/hamlet.xml", indexer, dir);
            assertFullTextQuery (indexTestSupport, "title", "TEST", 1);
        */
    }
    
    @Test
    public void testStoreBinary () throws Exception {
        XmlIndexer indexer = new XmlIndexer(STORE_DOCUMENT);
        IndexWriter indexWriter = indexer.newIndexWriter(dir);
        indexer.storeDocument(indexWriter, "/lux/compiler/test-module.xqy", 
                getClass().getClassLoader().getResourceAsStream("lux/compiler/test-module.xqy"));
        indexWriter.close();
    }

    @Test @Ignore
    public void testIndexPathValuesOneDoc() throws Exception {
        XmlIndexer indexer = new XmlIndexer (INDEX_PATHS | INDEX_VALUES);
        IndexWriter indexWriter = indexer.newIndexWriter(dir);
        indexer.indexDocument(indexWriter, "/lux/hamlet.xml", getClass().getClassLoader().getResourceAsStream("lux/hamlet.xml"));
        indexWriter.close();
        // hamlet.xml = 288815 bytes; indexed in 215040 bytes seems ok??
        // printAllTerms(new IndexTestSupport(indexer, dir));
    }

    @Test
    public void testIndexPathValuesOnly() throws Exception {
        IndexTestSupport indexTestSupport = buildIndex ("path-values", INDEX_PATHS | INDEX_VALUES | BUILD_DOCUMENT);
        assertTotalDocs ();
        assertPathQuery(indexTestSupport);
    }
    
    @Test
    public void testIndexPathText () throws Exception {
        IndexTestSupport indexTestSupport = buildIndex ("path-text", INDEX_PATHS | INDEX_FULLTEXT | BUILD_DOCUMENT);
        assertTotalDocs ();
        assertPathQuery(indexTestSupport);
    }    

    @Test
    public void testIndexQNameValues() throws Exception {
        buildIndex ("qname-values and docs", INDEX_QNAMES | INDEX_VALUES | STORE_DOCUMENT | BUILD_DOCUMENT);
        assertTotalDocs ();
    }

    @Test
    public void testIndexQNameText() throws Exception {
        IndexTestSupport indexTestSupport = buildIndex ("qname-text and docs", INDEX_QNAMES | INDEX_FULLTEXT | STORE_DOCUMENT | BUILD_DOCUMENT);
        assertFullTextQuery (indexTestSupport, "PERSONA", "ROSENCRANTZ", 4);
        assertTotalDocs ();
    }
    
    @Test
    public void testIndexQNameTextOnly() throws Exception {
        // IndexTestSupport indexTestSupport = 
        buildIndex ("qname-text", INDEX_QNAMES | INDEX_FULLTEXT | BUILD_DOCUMENT);
        assertTotalDocs ();
        // printAllTerms(indexTestSupport);
    }

    @Test
    public void testIndexPathValues() throws Exception {
        buildIndex ("path-values and docs", INDEX_PATHS | INDEX_VALUES | STORE_DOCUMENT | BUILD_DOCUMENT);
        assertTotalDocs ();
    }

    @Test
    public void testIndexQNamesAndPaths() throws Exception {
        IndexTestSupport its = buildIndex ("qnames and paths and docs", INDEX_QNAMES | INDEX_PATHS | STORE_DOCUMENT | BUILD_DOCUMENT);
        assertTotalDocs ();
        its.close();
        buildIndex ("qnames and paths", INDEX_QNAMES | INDEX_PATHS | BUILD_DOCUMENT);
    }

    @Test
    public void testIndexQNamesAndPathsOnly() throws Exception {
        buildIndex ("qnames and paths", INDEX_QNAMES | INDEX_PATHS | BUILD_DOCUMENT);
        assertTotalDocs ();
    }

    @Test
    public void testStoreDocuments() throws Exception {
        buildIndex ("xml storage", STORE_DOCUMENT | BUILD_DOCUMENT);
        assertTotalDocs ();
    }

    @Test
    public void testStoreBinaryDocs() throws Exception {
        buildIndex ("xml binary storage", STORE_TINY_BINARY | STORE_DOCUMENT | BUILD_DOCUMENT);
        assertTotalDocs ();
    }
    
    @Test
    public void testXPathIndexes () throws Exception {
        XmlIndexer indexer = new XmlIndexer (BUILD_DOCUMENT);
        indexer.getConfiguration().addField(new XPathField("nodecount", "count(//node())", null, Store.NO, Type.INT));
        indexer.getConfiguration().addField(new XPathField("doctype", "name(/*)", null, Store.NO, Type.STRING));
        IndexTestSupport indexTestSupport = buildIndex("xpath", indexer);
        assertXPathIntField(indexTestSupport);
        assertXPathStringField(5, "doctype", "ACT", indexTestSupport);
        if (GATHER_TIMING) {
            for (int i = 0; i < 5; i++) {
                reset();
                indexTestSupport = buildIndex("xpath", indexer);
            }
        }
    }

    @Test
    public void testMultipleXPathIndexes () throws Exception {
        XmlIndexer indexer = new XmlIndexer (BUILD_DOCUMENT);
        // SCENE comes in as ACT/*[2] - immediately following TITLE
        // These can be encoded within a single XPath - we don't allow multiple indexes with the same name
        indexer.getConfiguration().addField(new XPathField("x", "name(/*/*[2]),name(/*)", null, Store.NO, Type.STRING));
        IndexTestSupport indexTestSupport = buildIndex("xpath", indexer);
        assertXPathStringField(25, "x", "SCENE", indexTestSupport);
    }
    
    @Test
    public void testMultipleXPathIndexesFail () throws Exception {
        XmlIndexer indexer = new XmlIndexer (BUILD_DOCUMENT);
        // SCENE comes in as ACT/*[2] - immediately following TITLE
        indexer.getConfiguration().addField(new XPathField("x", "name(/*/*[2])", null, Store.NO, Type.STRING));
        try {
            indexer.getConfiguration().addField(new XPathField("x", "name(/*)", null, Store.NO, Type.STRING));
            assertTrue ("expected exception not thrown", false);
        } catch (IllegalStateException e) {
            assertEquals ("Duplicate field name: x", e.getMessage());
        }
    }
    
    @Test
    public void testXPathIndexNamespace () throws Exception {
        IndexConfiguration indexConfig = new IndexConfiguration();
        indexConfig.defineNamespaceMapping("", "");
        indexConfig.defineNamespaceMapping("x", "http://lux.net{test}");
        indexConfig.addField(new XPathField("title", "//x:title", new KeywordAnalyzer(), Store.NO, Type.STRING));
        XmlIndexer indexer = new XmlIndexer (indexConfig);
        IndexTestSupport indexTestSupport = new IndexTestSupport ("lux/reader-test-ns.xml", indexer, dir);
        assertXPathStringField(2, "title", "TEST", indexTestSupport);
    }
    
    @Before
    public void setup() {
        dir = new RAMDirectory();
    }
    
    @After
    public void cleanup() {
        dir.close();
    }
    
    private IndexTestSupport buildIndex (String desc, int options) throws XMLStreamException, IOException, SaxonApiException {
        XmlIndexer indexer = new XmlIndexer (options);
        IndexTestSupport index = buildIndex(desc, indexer);
        if (GATHER_TIMING) {
            for (int i = 0; i < 3; i++) {
                reset();
                indexer = new XmlIndexer (options);
                index = buildIndex (desc, indexer);
            }
        }
        return index;
    }

    private IndexTestSupport buildIndex(String desc, XmlIndexer indexer) throws XMLStreamException, IOException, SaxonApiException {
        long t0 = System.currentTimeMillis();
        IndexTestSupport indexTestSupport = new IndexTestSupport ("lux/hamlet.xml", indexer, dir);
        return indexTestSupport;
    }

    @SuppressWarnings("unused")
    private void printAllTerms(IndexTestSupport indexTestSupport) throws Exception {
        indexTestSupport.printAllTerms();
    }
    
    private void assertTotalDocs() throws IOException {
        LuxSearcher searcher = new LuxSearcher(dir);
        DocIdSetIterator results = searcher.search(new MatchAllDocsQuery());
        int count = 0;
        while (results.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
            ++count;
        }
        assertEquals (6641, count);
        /*
        */
        searcher.close();
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
