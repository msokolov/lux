package lux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import lux.api.Evaluator;
import lux.api.LuxException;
import lux.api.QueryStats;
import lux.api.ResultSet;
import lux.saxon.Saxon;
import lux.saxon.SaxonExpr;
import lux.saxon.UnOptimizer;
import lux.xpath.AbstractExpression;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Check a variety of XPath queries, ensuring that results are consistent with those 
 * observed when optimizations are undone, and observe that expected optimizations are 
 * in fact being applied.
 * 
 * @author sokolov
 *
 */
public class SearchTest extends SearchBase {
    
    private static final int MIL = 1000000;

    @Test
    public void testSearchAllDocs() throws Exception {
        ResultSet<?> results = assertSearch("/", QUERY_EXACT);
        assertEquals (totalDocs, results.size());
    }
    
    @Test
    public void testCountAllDocs () throws Exception {
        ResultSet<?> results = assertSearch ("count(/)", QUERY_NO_DOCS, totalDocs);
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
    public void testPathOrder () {
        // make sure that order is *not* significant in the generated query; 
        // it should be (SCENE AND ACT):
        // 120 = 20 (scenes) * 5 (acts) in 1 /PLAY + 20 scenes in 5 /ACT documents
        assertSearch ("120", "count(//SCENE/root()//ACT)", 0, 6);
    }
    
    @Test
    public void testSearchAct() throws Exception {
        // path indexes make this exact
        ResultSet<?> results = assertSearch ("/ACT", QUERY_EXACT);
        assertEquals (elementCounts.get("ACT") + 0, results.size());
    }
    
    @Test
    public void testSearchActScene() throws Exception {
        ResultSet<?> results = assertSearch("/ACT/SCENE", QUERY_MINIMAL);
        assertEquals (elementCounts.get("SCENE") + 0, results.size());
    }
    
    @Test
    public void testSearchAllScenes() throws Exception {
        ResultSet<?> results = assertSearch("//SCENE", QUERY_MINIMAL);
        // every SCENE, in its ACT and in the PLAY
        assertEquals (elementCounts.get("SCENE") * 3, results.size());
    }
    
    @Test
    public void testSearchAllSceneDocs() throws Exception {
        ResultSet<?> results = assertSearch("(/)[.//SCENE]", QUERY_EXACT);
        // every SCENE, in its ACT and in the PLAY
        assertEquals (elementCounts.get("SCENE") + elementCounts.get("ACT") + 1, results.size());
    }
    
    @Test
    public void testSearchAllSceneDocsRoot() throws Exception {
        ResultSet<?> results = assertSearch("//SCENE/root()", QUERY_EXACT);
        // every SCENE, in its ACT and in the PLAY
        assertEquals (elementCounts.get("SCENE") + elementCounts.get("ACT") + 1, results.size());
    }
    
    @Test
    public void testCountDocs () throws Exception {
        // every SCENE, in its ACT and in the PLAY
        int sceneDocCount = elementCounts.get("SCENE") + elementCounts.get("ACT") + 1;

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
        Saxon saxon = getEvaluator();
        SaxonExpr saxonExpr = saxon.compile(xpath);
        ResultSet<?> results = saxon.evaluate(saxonExpr);
        System.out.println ("query evaluated in " + (System.currentTimeMillis() - t) + " msec,  retrieved " + results.size() + " result");
        AbstractExpression aex = saxon.getTranslator().exprFor(saxonExpr.getXPathExecutable().getUnderlyingExpression().getInternalExpression());
        aex = new UnOptimizer(indexer.getOptions()).unoptimize(aex);
        SaxonExpr baseline = saxon.compile(aex.toString());
        ResultSet<?> baseResult = saxon.evaluate(baseline);
        assertEquals ("result count mismatch for: " + saxonExpr.toString(), baseResult.size(), results.size());        
    }
    
    @Test
    public void testComparisonPredicate () {
        long t = System.currentTimeMillis();
        String xpath = "//SCNDESCR[. >= //PERSONA]";
        Saxon saxon = getEvaluator();
        SaxonExpr saxonExpr = saxon.compile(xpath);
        ResultSet<?> results = saxon.evaluate(saxonExpr);
        System.out.println ("query evaluated in " + (System.currentTimeMillis() - t) + " msec,  retrieved " + results.size() + " results");
        AbstractExpression aex = saxon.getTranslator().exprFor(saxonExpr.getXPathExecutable().getUnderlyingExpression().getInternalExpression());
        aex = new UnOptimizer(indexer.getOptions()).unoptimize(aex);
        SaxonExpr baseline = saxon.compile(aex.toString());
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
        // Our first naive implementation tried to fetch all relevant documents
        // using a single database query - this test tests multiple independent
        // sequences (they were broken in that first impl).
        // /PLAY/PERSONAE/PGROUP/PERSONA
        ResultSet<?> results = assertSearch("count (//PERSONA[.='ROSENCRANTZ'])", 0);
        assertEquals ("4", results.iterator().next().toString());
        results = assertSearch("count (//PERSONA[.='ROSENCRANTZ']) + count(//PERSONA[.='GUILDENSTERN'])", 0);
        assertEquals (1, results.size());
        assertEquals ("8", results.iterator().next().toString());       
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
        // Failed to optimize this in two different ways.
        //
        // find the 4th document with a SCENE in it; We shouldn't need to retrieve the first 3
        // 
        // Even if we do retrieve those, we shouldn't need to get the rest of them,
        // but actually Saxon decides it needs to sort one sequence or another 
        // so we end up retrieving all the documents that match the query and don't short-circuit.

        assertSearch ("KING CLAUDIUS", "subsequence((/)[.//SCENE], 4, 1)//SPEECH[1]/SPEAKER/string()", null, 4);
        assertSearch ("BERNARDO", "(//SCENE/SPEECH)[1]/SPEAKER/string()", null, 1);
    }
    
    @Test
    public void testRoot () {
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
        assertSearch ("2", "count(/SPEECH[contains(., 'philosophy')])", null, 1138);
        assertSearch ("28", "count(/SPEECH[contains(., 'Horatio')])", null, 1138);
        assertSearch ("8", "count(//SPEECH[contains(., 'philosophy')])", null, 1164);
        // saxon cleverly optimizes this and gets rid of the intersect
        assertSearch ("1", "count(/SPEECH[contains(., 'philosophy')] intersect /SPEECH[contains(., 'Horatio')])", null, 1138);
    }
    
    @Test
    public void testDocumentIdentity() throws Exception {
        /* This test confirms that document identity is preserved when creating Saxon documents. */
        assertSearch ("1", "count(//SPEECH[contains(., 'philosophy')] intersect /SPEECH[contains(., 'Horatio')])", null, 1164);        
    }
    
    @Test
    public void testDocumentOrder() throws Exception {
        /* This test confirms that document ordering is correct since if document order in Saxon
         * is not the same as document order in Lucene, then the first 31st document will not be
         * what we expect.  31 is a magic number because /PLAY has 20 /PLAY/ACT/SCENE, 
         * /ACT 1 has 5 /ACT/SCENE, then those 5 are repeated as /SCENE. The 31st should be 
         * /ACT[2]/SCENE[1], but since this will already have been created, its Saxon document 
         * number would be low using the built-in numbering scheme, and the order mismatch causes 
         * Saxon to terminate the intersection prematurely. */
        assertSearch ("5", "count(/ACT/SCENE intersect subsequence(//SCENE, 1, 30))", null, 7);
        assertSearch ("6", "count(/ACT/SCENE intersect subsequence(//SCENE, 1, 31))", null, 8);
    }
    
    @Test
    public void testPaths () throws Exception {
        // test path ordering:
        assertSearch (null, "/ACT/PLAY", null, 0);
        assertSearch (null, "//ACT//PLAY", null, 0);
        // test path distance:
        assertSearch ("Where is your son?", "string(/PLAY/ACT[4]/SCENE[1]/SPEECH[1]/LINE[3])", null, 1);
        // FIXME:
        //assertSearch ("Where is your son?", "/PLAY/ACT[4]/SCENE[1]/SPEECH[1]/LINE[3]/string()", null, 1);
        // Q: who decides what serialization to use?
        //assertSearch ("Where is your son?", "/PLAY/ACT[4]/SCENE[1]/SPEECH[1]/LINE[3]", null, 1);
        assertSearch ("Where is your son?", "string((/PLAY/ACT[4]/*/*/LINE)[3])", null, 1);
        // no result, but we can't tell from the query and have to retrieve the document and process it
        assertSearch (null, "/PLAY/ACT[4]/*/*/*/*/LINE", null, 1);
    }
    
    private ResultSet<?> assertSearch(String query) throws LuxException {
        return assertSearch (query, 0);
    }
    
    protected ResultSet<?> assertSearch(String query, Integer props) throws LuxException {
        return assertSearch(query, props, null);
    }

    protected void assertSearch(String expectedResult, String query, Integer props, Integer docCount) throws LuxException {
        ResultSet<?> results = assertSearch (query, props, docCount);
        boolean hasResults = results.iterator().hasNext();
        String result = hasResults ? results.iterator().next().toString() : null;
        if (expectedResult == null) {            
            assertTrue ("results not empty, got: " + result, !hasResults);
            return;
        }
        assertTrue ("no results", hasResults);
        assertEquals ("incorrect query result", expectedResult, result);
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
    protected ResultSet<?> assertSearch(String query, Integer props, Integer docCount) throws LuxException {
        Evaluator eval = getEvaluator();
        SaxonExpr expr = (SaxonExpr) eval.compile(query);
        System.out.println (expr.getXPathExecutable().getUnderlyingExpression().getInternalExpression());
        ResultSet<?> results = (ResultSet<?>) eval.evaluate(expr);
        QueryStats stats = eval.getQueryStats();
        System.out.println (String.format("t=%d, tsearch=%d, tretrieve=%d, query=%s", 
                stats.totalTime/MIL, stats.collectionTime/MIL, stats.retrievalTime/MIL, query));
        System.out.println (String.format("cache hits=%d, misses=%d", stats.cacheHits, stats.cacheMisses));
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
            assertEquals ("incorrect document count", docCount.intValue(), stats.docCount);
        }
        return results;
    }

}
