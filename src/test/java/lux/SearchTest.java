package lux;

import static org.junit.Assert.*;

import static lux.IndexTestSupport.*;

import java.util.Iterator;

import lux.api.LuxException;
import lux.api.QueryStats;
import lux.api.ResultSet;
import lux.saxon.Saxon;
import lux.saxon.SaxonExpr;
import lux.saxon.UnOptimizer;
import lux.xpath.AbstractExpression;
import lux.xquery.XQuery;

import net.sf.saxon.s9api.XdmNode;

import org.apache.lucene.queryParser.ParseException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Check a variety of XPath queries, ensuring that results when executed using the default indexing
 * settings, as provided by IndexTestSupport, are correct, 
 * and test that expected optimizations are in fact being applied. 
 */
public class SearchTest {
    
    private static final int MIL = 1000000;
    private static IndexTestSupport index;
    private static int totalDocs;
    
    @BeforeClass
    public static void setup () throws Exception {
        index = new IndexTestSupport();
        // new IndexTestSupport(XmlIndexer.INDEX_QNAMES|XmlIndexer.STORE_XML|XmlIndexer.BUILD_JDOM, new RAMDirectory());
        totalDocs= index.totalDocs;
    }
    
    @AfterClass
    public static void tearDown() throws Exception {
        index.close();
    }
    
    @Test
    public void testSearchAllDocs() throws Exception {
        ResultSet<?> results = assertSearch("/", IndexTestSupport.QUERY_EXACT);
        assertEquals (index.totalDocs, results.size());
    }
    
    @Test
    public void testCountAllDocs () throws Exception {
        ResultSet<?> results = assertSearch ("count(/)", QUERY_NO_DOCS, totalDocs);
        assertEquals (String.valueOf(totalDocs), results.iterator().next().toString());

        results = assertSearch ("count(collection())", QUERY_NO_DOCS, totalDocs);
        assertEquals (String.valueOf(totalDocs), results.iterator().next().toString());

        results = assertSearch ("count(lux:search('*:*'))", QUERY_NO_DOCS, totalDocs);
        assertEquals (String.valueOf(totalDocs), results.iterator().next().toString());
    }
    
    @Test
    public void testExists () throws Exception {
        assertSearch ("true", "exists(/)", QUERY_NO_DOCS, 1);
        assertSearch ("true", "exists(//SCENE)", QUERY_NO_DOCS, 1);
        assertSearch ("false", "exists(//foo)", QUERY_NO_DOCS, 0);
        assertSearch ("true", "exists(//SCENE/root())", QUERY_NO_DOCS, 1);
        assertSearch ("true", "exists(//SCENE) and exists(//ACT)", QUERY_NO_DOCS, 2);
        assertSearch ("true", "exists(//SCENE/root()//ACT)", QUERY_NO_DOCS, 1);
        assertSearch ("true", "exists((/)[.//SCENE and .//ACT])", QUERY_NO_DOCS, 1);
        assertSearch ("true", "exists(//ACT//SCENE)", QUERY_NO_DOCS, 1);
    }
    
    @Test
    public void testEmpty () throws Exception {
        ResultSet<?> results = assertSearch ("empty(/)", QUERY_NO_DOCS, 1);
        assertEquals ("false", results.iterator().next().toString());
        assertSearch ("false", "empty(//SCENE)", QUERY_NO_DOCS, 1);
        assertSearch ("true", "empty(//foo)", QUERY_NO_DOCS, 0);
        assertSearch ("false", "empty(//SCENE/root())", QUERY_NO_DOCS, 1);
        assertSearch ("true", "empty(//SCENE) or empty(//foo)", QUERY_NO_DOCS, 1);
        assertSearch ("false", "empty(//SCENE/root()//ACT)", QUERY_NO_DOCS, 1);
        assertSearch ("false", "empty((/)[.//SCENE and .//ACT])", QUERY_NO_DOCS, 1);
    }

    @Test
    public void testNot() throws Exception {
        ResultSet<?> results = assertSearch ("not(/)", QUERY_NO_DOCS, 1);
        assertEquals ("false", results.iterator().next().toString());
        assertSearch  ("false", "not(//SCENE)", QUERY_NO_DOCS, 1);
        assertSearch  ("true", "not(//foo)", QUERY_NO_DOCS, 0);
        assertSearch ("false", "not(//SCENE/root())", QUERY_NO_DOCS, 1);
        assertSearch ("true", "not(//SCENE) or not(//foo)", QUERY_NO_DOCS, 1);
        assertSearch ("false", "not(//SCENE/root()//ACT)", QUERY_NO_DOCS, 1);
        assertSearch ("false", "not((/)[.//SCENE and .//ACT])", QUERY_NO_DOCS, 1);
        assertSearch ("true", "not(//SCENE//ACT)", QUERY_NO_DOCS, 0);
    }
    
    @Test
    public void testPathOrder () throws Exception {
        // Make sure that the Optimizer doesn't incorrectly assert 
        // order is *not* significant in the generated query; 
        // it should be (SCENE AND ACT):
        // Overall there are 20 scenes in 5 acts in 1 play
        // 40 = 20 (SCENEs in /PLAY) + 20 (SCENEs in the 5 /ACTs together)
        assertSearch ("40", "count(//ACT/root()//SCENE)", 0, 6);
        // 10 = 5 (ACTs in /PLAY) + 5 /ACT documents.
        assertSearch ("10", "count(//SCENE/root()//ACT)", 0, 6);
        // Why did we think this?:
        // 120 = 20 (scenes) * 5 (acts) in 1 /PLAY + 20 scenes in 5 /ACT documents
    }
    
    @Test
    public void testSearchAct() throws Exception {
        // path indexes make this exact
        ResultSet<?> results = assertSearch ("/ACT", QUERY_EXACT);
        assertEquals (index.elementCounts.get("ACT") + 0, results.size());
        // Make sure that collection() is optimized
        results = assertSearch ("collection()/ACT", QUERY_EXACT);
        assertEquals (index.elementCounts.get("ACT") + 0, results.size());
        // and that references to variables are optimized
        results = assertSearch ("let $context := collection() return $context/ACT", QUERY_EXACT);
        assertEquals (index.elementCounts.get("ACT") + 0, results.size());
    }
    
    @Test
    public void testSearchActScene() throws Exception {
        ResultSet<?> results = assertSearch("/ACT/SCENE", QUERY_MINIMAL);
        assertEquals (index.elementCounts.get("SCENE") + 0, results.size());
    }
    
    @Test
    public void testSearchAllScenes() throws Exception {
        ResultSet<?> results = assertSearch("/ACT", QUERY_MINIMAL);
        assertEquals (5, results.size());
        XdmNode node = (XdmNode) results.iterator().next();
        String actURI = node.getDocumentURI().toString();
        results = assertSearch("//SCENE", QUERY_MINIMAL);
        // every SCENE, in its ACT and in the PLAY
        assertEquals (index.elementCounts.get("SCENE") * 3, results.size());
        Iterator<?> iter = results.iterator();
        for (int i = 0; i < index.elementCounts.get("SCENE"); i++) {
            // each scene, from the /PLAY document
            node = (XdmNode) iter.next();
            assertEquals ("lux://lux/hamlet.xml", node.getDocumentURI().toString());
            assertEquals ("lux://lux/hamlet.xml", node.getBaseURI().toString());                
        }
        XdmNode act1 = (XdmNode) iter.next();
        assertEquals (actURI, act1.getBaseURI().toString());
    }
    
    @Test
    public void testSearchAllSceneDocs() throws Exception {
        ResultSet<?> results = assertSearch("(/)[.//SCENE]", QUERY_EXACT);
        // every SCENE, in its ACT and in the PLAY
        assertEquals (index.elementCounts.get("SCENE") + index.elementCounts.get("ACT") + 1, results.size());
    }
    
    @Test
    public void testSearchAllSceneDocsRoot() throws Exception {
        ResultSet<?> results = assertSearch("//SCENE/root()", QUERY_EXACT);
        // every SCENE, in its ACT and in the PLAY
        assertEquals (index.elementCounts.get("SCENE") + index.elementCounts.get("ACT") + 1, results.size());
    }
    
    @Test
    public void testCountDocs () throws Exception {
        // every SCENE, in its ACT and in the PLAY
        int sceneDocCount = index.elementCounts.get("SCENE") + index.elementCounts.get("ACT") + 1;

        ResultSet<?> results = assertSearch("count (//SCENE/root())", QUERY_NO_DOCS);
        assertResultValue(results, sceneDocCount);
        
        results = assertSearch("count ((/)[.//SCENE])", QUERY_NO_DOCS);
        assertResultValue(results, sceneDocCount);

        results = assertSearch("count (//SCENE/ancestor::document-node())", QUERY_NO_DOCS);
        assertResultValue(results, sceneDocCount);

        results = assertSearch("count (/descendant-or-self::SCENE/root())", QUERY_NO_DOCS);
        assertResultValue(results, sceneDocCount);
        
        results = assertSearch("count (/descendant::SCENE/root())", QUERY_NO_DOCS);
        assertResultValue(results, sceneDocCount);
    }

    private void assertResultValue(ResultSet<?> results, int sceneDocCount) {
        assertEquals (String.valueOf(sceneDocCount), results.iterator().next().toString());
    }
    
    @Test
    public void testSyntaxError () throws Exception {
        try {
            assertSearch ("hey bad boy");
            assertTrue ("expected LuxException to be thrown for syntax error", false);
        } catch (LuxException e) {
        }
    }
    
    @Test
    public void testTextComparison () {
        long t = System.currentTimeMillis();
        String xpath = "//SCNDESCR >= //PERSONA";
        Saxon saxon = index.getEvaluator();
        SaxonExpr saxonExpr = saxon.compile(xpath);
        ResultSet<?> results = saxon.evaluate(saxonExpr);
        System.out.println ("query evaluated in " + (System.currentTimeMillis() - t) + " msec,  retrieved " + results.size() + " result");
        AbstractExpression aex = saxonExpr.getXPath();
        aex = new UnOptimizer(index.indexer.getOptions()).unoptimize(aex);
        SaxonExpr baseline = saxon.compile(aex.toString());
        ResultSet<?> baseResult = saxon.evaluate(baseline);
        assertEquals ("result count mismatch for: " + saxonExpr.toString(), baseResult.size(), results.size());        
    }
    
    @Test
    public void testComparisonPredicate () {
        long t = System.currentTimeMillis();
        String xpath = "//SCNDESCR[. >= //PERSONA]";
        Saxon saxon = index.getEvaluator();
        SaxonExpr saxonExpr = saxon.compile(xpath);
        ResultSet<?> results = saxon.evaluate(saxonExpr);
        System.out.println ("query evaluated in " + (System.currentTimeMillis() - t) + " msec,  retrieved " + results.size() + " results");
        XQuery optimized = saxonExpr.getXQuery();
        XQuery unoptimized = new UnOptimizer(index.indexer.getOptions()).unoptimize(optimized);
        SaxonExpr baseline = saxon.compile(unoptimized.toString());
        ResultSet<?> baseResult = saxon.evaluate(baseline);
        assertEquals ("result count mismatch for: " + saxonExpr.toString(), baseResult.size(), results.size());
    }
    
    @Test
    public void testConstantExpression() throws Exception {
        // This resolves to a constant (Literal=true()) XPath expression and generates
        // a null Lucene query.  Make sure we don't try to execute the query.
        ResultSet<?> results = assertSearch("'remorseless' or descendant::text", QUERY_CONSTANT);
        assertEquals (1, results.size());
    }
    
    @Test
    public void testMultipleAbsolutePaths() throws Exception {
        // /PLAY/PERSONAE/PGROUP/PERSONA
        assertSearch("4", "count (//PERSONA[.='ROSENCRANTZ'])", 0, 4);
        assertSearch("4", "count (//PERSONA[.='GUILDENSTERN'])", 0, 4);
        // Our first naive implementation tried to fetch all relevant documents
        // using a single database query - this test tests multiple independent
        // sequences.
        // we retrieved 8 documents from search, because there are two queries generated, but
        // only 5 unique docs, and we cache, so only 5 docs are actually retrieved
        assertSearch("8", "count (//PERSONA[.='ROSENCRANTZ']) + count(//PERSONA[.='GUILDENSTERN'])", 0, 8);
    }
    
    @Test
    public void testLazyEvaluation () throws Exception {
        // These expressions are optimized in the sense that lux evaluates them all by retrieving
        // only the minimal number of required documents (with the available indexes).
        
        // Note this relies on Lucene's default sort by order of insertion (ie by docid)
        assertSearch ("BERNARDO", "subsequence(//SCENE, 1, 1)/SPEECH[1]/SPEAKER/string()", null, 1);
        assertSearch ("BERNARDO", "(//SCENE)[1]/SPEECH[1]/SPEAKER/string()", null, 1);
        // /PLAY/ACT[1]/SCENE[1], /ACT[1]/SCENE[1], /SCENE[1], /SCENE[2], /SCENE[3], /SCENE[4]
        // count reduced from 6 to 4 by path queries; skip /PLAY and /ACT[1]
        assertSearch ("HAMLET", "subsequence(/SCENE, 4, 1)/SPEECH[1]/SPEAKER/string()", null, 4);
    }
    
    @Test
    public void testSkipDocs () throws Exception {
        // Earlier implementations failed to indicate that the returned sequence of documents is sorted in document
        // order, causing Saxon to pull the entire result sequence.
        //
        // TODO: We shouldn't need to retrieve (the text of) the first 3 documents in the first query below since
        // they are going to be discarded

        assertSearch ("KING CLAUDIUS", "subsequence((/)[.//SCENE], 4, 1)//SPEECH[1]/SPEAKER/string()", null, 4);
        assertSearch ("BERNARDO", "(//SCENE/SPEECH)[1]/SPEAKER/string()", null, 1);
    }
    
    @Test
    public void testSkipDocs2 () throws Exception {
        assertSearch ("BERNARDO", "(//SCENE/SPEECH)[1]/SPEAKER/string()", null, 1);
    }
    
    @Test
    public void testRoot () throws Exception {
        assertSearch ("KING CLAUDIUS", "(//SCENE/root())[4]//SPEECH[1]/SPEAKER/string()", null, 4);
        assertSearch ("KING CLAUDIUS", "subsequence(//SCENE/root(), 4, 1)//SPEECH[1]/SPEAKER/string()", null, 4);        
    }
    
    @Test @Ignore
    public void testOptimizeLast () throws Exception {
        // Failed to optimize this.
        // 
        // We should be able to retrieve the last document, and then get its last speech      
        // best idea for optimizing this is to add pagination to lux:search
        assertSearch ("PRINCE FORTINBRAS", "(lux:search('lux_elt_name_ms:SPEECH')[last()]//SPEECH)[last()]/SPEAKER/string()", null, 1);
        assertSearch ("PRINCE FORTINBRAS", "(//SPEECH)[last()]/SPEAKER/string()", null, 1164);
    }
    
    @Test
    public void testIntersection () throws Exception {
        // There were issues with 
        // document order, which we have to assert in order to get lazy evaluation.
        // Intersect in particular exposes the problem since it's optimized based on
        // correct sorting (tip from Michael Kay).
        // NB - count was 1164; reduced to 1138 by path query (20 scenes + 5 acts + 1 play = 26).
        // Then reduced to 2! by a full text term query
        assertSearch ("2", "count(/SPEECH[contains(., 'philosophy')])", null, 2);
        // FIXME - why is this 141 and not 28?
        assertSearch ("28", "count(/SPEECH[contains(., 'Horatio')])", null, 141);
        assertSearch ("8", "count(//SPEECH[contains(., 'philosophy')])", null, 7);
        // saxon cleverly optimizes this and gets rid of the intersect
        // but FIXME our optimizer fails to see the opportunity for a word query
        assertSearch ("1", "count(/SPEECH[contains(., 'philosophy')] intersect /SPEECH[contains(., 'Horatio')])", null, 1138);
    }
    
    @Test
    public void testDocumentIdentity() throws Exception {
        /* This test confirms that document identity is preserved when creating Saxon documents. 
         * document count was 1670 using path indexes; reduced to 88 using full text queries
         * TODO: Why is this not more optimal?
         * */
        assertSearch ("1", "count(//SPEECH[contains(., 'philosophy')] intersect /SPEECH[contains(., 'Horatio')])", null, 88, 87);        
    }
    
    @Test
    public void testDocumentOrder() throws Exception {
        /* This test confirms that the document ordering asserted by the Optimizer 
         * is correct since if document order in Saxon
         * is not the same as document order in Lucene, then the first 31st document will not be
         * what we expect.  31 is a magic number because /PLAY has 20 /PLAY/ACT/SCENE, 
         * /ACT 1 has 5 /ACT/SCENE, then those 5 are repeated as /SCENE. The 31st should be 
         * /ACT[2]/SCENE[1], but since this will already have been created, its Saxon document 
         * number would be low using the built-in numbering scheme, and the order mismatch causes 
         * Saxon to terminate the intersection prematurely. */
        assertSearch ("5", "count(/ACT/SCENE intersect subsequence(//SCENE, 1, 30))", null, 9, 8);
        assertSearch ("6", "count(/ACT/SCENE intersect subsequence(//SCENE, 1, 31))", null, 10, 8);
    }
    
    @Test
    public void testPaths () throws Exception {
        // test path ordering:
        assertSearch (null, "/ACT/PLAY", null, 0);
        assertSearch (null, "//ACT//PLAY", null, 0);
        // test path distance:
        assertSearch ("Where is your son?", "string(/PLAY/ACT[4]/SCENE[1]/SPEECH[1]/LINE[3])", null, 1);
        // Q: who decides what serialization to use?
        //assertSearch ("Where is your son?", "/PLAY/ACT[4]/SCENE[1]/SPEECH[1]/LINE[3]", null, 1);
        assertSearch ("Where is your son?", "string((/PLAY/ACT[4]/*/*/LINE)[3])", null, 1);
        // no result, but we can't tell from the query and have to retrieve the document and process it
        assertSearch (null, "/PLAY/ACT[4]/*/*/*/*/LINE", null, 1);
    }

    @Test
    public void testElementFullTextPhrase () throws Exception {
        // test phrase query generation
        // also handling of capitalization and tokenization (w/punctuation)
        assertSearch ("5", "count(//LINE[.='Holla! Bernardo!'])", null, 5, 5);
        assertSearch ("0", "count(//LINE[.='Holla!'])", null, 5, 5);
        assertSearch ("0", "count(//LINE[.='Holla Bernardo'])", null, 5, 5);
        assertSearch ("5", "count(//LINE[lower-case(.)='holla! bernardo!'])", null, 5, 5);
        // check stop word handling
        assertSearch ("<LINE>Where is your son?</LINE>", "//LINE[.='Where is your son?']", null, 5, 5);
    }
    
    @Test public void testFullText () throws Exception {
        assertSearch ("Where is your son?", "//*[.='Where is your son?']/string()", null, 5, 5);
    }
    
    @Test public void testContains () throws Exception {
        assertSearch ("5", "count(//LINE[contains(.,'Holla')])", null, 5, 5);
        assertSearch ("true", "contains(/PLAY,'Holla')", null, 1, 1);
    }
    
    @Test public void testLuxSearch () throws Exception {
        assertSearch ("5", "count(lux:search('\"holla bernardo\"'))", null, 5, 0);
        assertSearch ("5", "count(lux:search('<LINE:\"holla bernardo\"'))", null, 5, 0);
        try {
            assertSearch (null, "lux:search(1,2,3)", null, null, null);
            assertTrue ("expected exception", false);
        } catch (LuxException e) { }
        try {
            assertSearch (null, "lux:search(1,'xx')", null, null, null);
            assertTrue ("expected exception", false);
        } catch (LuxException e) { }
        try {
            assertSearch (null, "lux:search(':::')", null, null, null);
            assertTrue ("expected exception", false);
        } catch (ParseException e) { }
        assertSearch ("65", "lux:count(text{'bernardo'})", null, 65, 0);
    }

    @Test 
    public void testTrailingStringCall () throws Exception {
        // FIXME - this isn't optimized as well as it could be; it has some Booleans in it?
        assertSearch ("Where is your son?", "/PLAY/ACT[4]/SCENE[1]/SPEECH[1]/LINE[3]/string()", null, 1);        
    }
        
    private ResultSet<?> assertSearch(String query) throws LuxException {
        return assertSearch (query, 0);
    }
    
    protected ResultSet<?> assertSearch(String query, Integer props) throws LuxException {
        return assertSearch(query, props, null);
    }
    
    protected ResultSet<?> assertSearch(String query, Integer props, Integer docCount) throws LuxException {
        return assertSearch (query, props, docCount, null);
    }
    
    protected ResultSet<?> assertSearch(String expectedResult, String query, Integer facts, Integer docCount) throws Exception {
        return assertSearch (expectedResult, query, facts, docCount, null);
    }
    
    protected ResultSet<?> assertSearch(String expectedResult, String query, Integer props, Integer docCount, Integer cacheMisses) throws Exception {
        ResultSet<?> results = assertSearch (query, props, docCount, cacheMisses);
        if (results.getException() != null) {
            throw results.getException();
        }
        boolean hasResults = results.iterator().hasNext();
        String result = hasResults ? results.iterator().next().toString() : null;
        if (expectedResult == null) {            
            assertTrue ("results not empty, got: " + result, !hasResults);
            return results;
        }
        assertTrue ("no results", hasResults);
        assertEquals ("incorrect query result", expectedResult, result);
        return results;
    }
    
    /**
     * Executes the query, ensures that the given properties hold, and returns the result set.
     * Prints some diagnostic statistics, including total elapsed time (t) and time spent in the 
     * search result collector (tsearch), which excludes any subseuqnet evaluation of results.
     * @param query an XPath query
     * @param props properties asserted to hold for the query evaluation
     * @return the query results
     * @throws LuxException
     */
    protected ResultSet<?> assertSearch(String query, Integer props, Integer docCount, Integer cacheMisses) throws LuxException {
        Saxon eval = index.getEvaluator();
        SaxonExpr expr = (SaxonExpr) eval.compile(query);
        System.out.println (expr);
        ResultSet<?> results = (ResultSet<?>) eval.evaluate(expr);
        QueryStats stats = eval.getQueryStats();
        System.out.println (String.format("t=%d, tsearch=%d, tretrieve=%d, query=%s", 
                stats.totalTime/MIL, stats.collectionTime/MIL, stats.retrievalTime/MIL, query));
        System.out.println (String.format("cache hits=%d, misses=%d", 
                eval.getDocReader().getCacheHits(), eval.getDocReader().getCacheMisses()));
        if (props != null) {
            if ((props & QUERY_EXACT) != 0) {
                assertEquals ("query is not exact", results.size(), stats.docCount);
            }
            if ((props & QUERY_CONSTANT) != 0) {
                assertEquals ("query is not constant", 0, stats.docCount);
            }
            if ((props & QUERY_MINIMAL) != 0) {
                // this is not the same as minimal, but is implied by it:
                assertTrue ("query is not minimal; retrieved " + stats.docCount + 
                " docs but only got " + results.size() + " results", 
                results.size() >= stats.docCount);
                // in addition we'd need to show that every document produced at least one result
            }
            if ((props & QUERY_NO_DOCS) != 0) {
                // This only makes sense because the main cost is usually retrieving and parsing documents
                // if we spend most of our time searching (in the collector), we didn't do a lot of xquery evaluation
                assertTrue ("query is not filter free", stats.retrievalTime / (stats.totalTime + 1.0) < 0.01);
            }
        }
        if (docCount != null) {
            assertEquals ("incorrect document result count", docCount.intValue(), stats.docCount);
        }
        if (cacheMisses != null) {
            assertEquals ("incorrect cache misses count", cacheMisses.intValue(), eval.getDocReader().getCacheMisses());            
        }
        return results;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
