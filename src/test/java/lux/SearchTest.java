package lux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;


import lux.api.Evaluator;
import lux.api.LuxException;
import lux.api.QueryStats;

import org.junit.Ignore;
import org.junit.Test;

public abstract class SearchTest extends SearchBase {
    
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
    
    protected List<?> assertSearch(String query, int props) throws LuxException {
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
        QueryStats stats = eval.getQueryStats();
        if (stats != null) {
            if ((props & QUERY_EXACT) != 0) {
                assertEquals (results.size(), stats.docCount);
            }
            if ((props & QUERY_MINIMAL) != 0) {
                // this is not the same as minimal, but is implied by it:
                assertTrue (results.size() >= stats.docCount);
                // in addition we'd need to show that every document produced at least one result
            }
            if ((props & QUERY_FILTER_FREE) != 0) {
                // if we spend < 1% of our time in the collector, we didn't do a lot of xquery evaluation
                assertTrue ((stats.collectionTime + 1) / (eval.getQueryStats().totalTime + 1.0) < 0.01);
            }
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
