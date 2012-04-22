package lux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import lux.api.Evaluator;
import lux.api.LuxException;
import lux.api.QueryStats;
import lux.api.ResultSet;
import lux.saxon.Saxon;
import lux.saxon.SaxonContext;
import lux.saxon.SaxonExpr;
import lux.saxon.UnOptimizer;
import lux.xpath.AbstractExpression;

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
        ResultSet<?> results = assertSearch ("count(/)", QUERY_FILTER_FREE);
        assertEquals (String.valueOf(totalDocs), results.iterator().next().toString());
    }
    
    @Test
    public void testSearchAct() throws Exception {
        ResultSet<?> results = assertSearch ("/ACT");
        assertEquals (elementCounts.get("ACT") + 0, results.size());
    }
    
    @Test
    public void testSearchActScene() throws Exception {
        ResultSet<?> results = assertSearch("/ACT/SCENE");
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

        ResultSet<?> results = assertSearch("count (//SCENE/root())", QUERY_FILTER_FREE);
        assertResultValue(results, sceneDocCount);
        
        results = assertSearch("count ((/)[.//SCENE])", QUERY_FILTER_FREE);
        assertResultValue(results, sceneDocCount);

        results = assertSearch("count (//SCENE/ancestor::document-node())", QUERY_FILTER_FREE);
        assertResultValue(results, sceneDocCount);

        results = assertSearch("count (/descendant-or-self::SCENE/root())", QUERY_FILTER_FREE);
        assertResultValue(results, sceneDocCount);
        
        results = assertSearch("count (/descendant::SCENE/root())", QUERY_FILTER_FREE);
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
        aex = new UnOptimizer().unoptimize(aex);
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
        System.out.println ("query evaluated in " + (System.currentTimeMillis() - t) + " msec,  retrieved " + results.size() + " result");
        AbstractExpression aex = saxon.getTranslator().exprFor(saxonExpr.getXPathExecutable().getUnderlyingExpression().getInternalExpression());
        aex = new UnOptimizer().unoptimize(aex);
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
    public void testCollectionScope() throws Exception {
        // /PLAY/PERSONAE/PGROUP/PERSONA
        ResultSet<?> results = assertSearch("count (//PERSONA[.='ROSENCRANTZ'])", 0);
        assertEquals ("4", results.iterator().next().toString());
        results = assertSearch("count (//PERSONA[.='ROSENCRANTZ']) + count(//PERSONA[.='GUILDENSTERN'])", 0);
        assertEquals (1, results.size());
        assertEquals ("8", results.iterator().next().toString());       
    }
    
    private ResultSet<?> assertSearch(String query) throws LuxException {
        return assertSearch (query, 0);
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
    protected ResultSet<?> assertSearch(String query, int props) throws LuxException {
        Evaluator eval = getEvaluator();
        ResultSet<?> results = (ResultSet<?>) eval.evaluate(eval.compile(query));
        QueryStats stats = eval.getQueryStats();
        System.out.println (String.format("t=%d, tsearch=%d, tretrieve=%d, query=%s", 
                stats.totalTime/MIL, stats.collectionTime/MIL, stats.retrievalTime/MIL, query));
        if ((props & QUERY_EXACT) != 0) {
            assertEquals ("query is not exact", results.size(), stats.docCount);
        }
        if ((props & QUERY_CONSTANT) != 0) {
            assertEquals ("query is not constant", 0, stats.docCount);
        }
        if ((props & QUERY_MINIMAL) != 0) {
            // this is not the same as minimal, but is implied by it:
            assertTrue (results.size() >= stats.docCount);
            // in addition we'd need to show that every document produced at least one result
        }
        if ((props & QUERY_FILTER_FREE) != 0) {
            // if we spend most of our time searching (in the collector), we didn't do a lot of xquery evaluation
            assertTrue ("query is not filter free", (stats.retrievalTime + 1) / (stats.totalTime + 1.0) < 0.01);
        }
        return results;
    }
    
    @Override
    public Saxon getEvaluator() {
        Saxon eval = new Saxon();
        eval.setContext(new SaxonContext(searcher));
        eval.setQueryStats (new QueryStats());
        return eval;
    }

}
