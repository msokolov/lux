package lux;

import static lux.IndexTestSupport.*;
import static org.junit.Assert.*;

import java.util.Iterator;

import lux.exception.LuxException;
import lux.saxon.UnOptimizer;
import lux.xpath.AbstractExpression;
import lux.xquery.XQuery;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmNode;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Check a variety of XPath queries, ensuring that results when executed using the default indexing
 * settings, as provided by IndexTestSupport, are correct, 
 * and test that expected optimizations are in fact being applied. 
 */
@RunWith (MultiThreadedRunner.class)
public class SearchTest extends BaseSearchTest {
    
    private static final String HAMLET_TITLE_MARKUP = "<TITLE>The Tragedy of Hamlet, Prince of Denmark</TITLE>";
    private static final String HAMLET_TITLE = "The Tragedy of Hamlet, Prince of Denmark";

    @BeforeClass
    public static void setup() throws Exception {
        setup ("lux/hamlet.xml");
    }
    
    @Test
    public void testSearchAllDocs() throws Exception {
        XdmResultSet results = assertSearch("/", IndexTestSupport.QUERY_EXACT);
        assertEquals (index.totalDocs, results.size());
    }
    
    @Test
    public void testCountAllDocs () throws Exception {
        XdmResultSet results = assertSearch ("count(/)", QUERY_NO_DOCS, totalDocs);
        assertEquals (String.valueOf(totalDocs), results.iterator().next().toString());

        results = assertSearch ("count(collection())", QUERY_NO_DOCS, totalDocs);
        assertEquals (String.valueOf(totalDocs), results.iterator().next().toString());

        results = assertSearch ("count(lux:search('*:*'))", QUERY_NO_DOCS, totalDocs);
        assertEquals (String.valueOf(totalDocs), results.iterator().next().toString());
    }

    @Test
    public void testCountActChildren () throws Exception {
        // Test an assumption about query accuracy:
        // span query slop is less precise than the XPath here, so 6 documents
        // must be examined even though only 1 (the PLAY) contains the five child ACTs
        assertSearch ("5", "count (/*/ACT)", 0, 6);
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
        XdmResultSet results = assertSearch ("empty(/)", QUERY_NO_DOCS, 1);
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
        XdmResultSet results = assertSearch ("not(/)", QUERY_NO_DOCS, 1);
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
    public void testNotExists() throws Exception {
        
    	// Trying to come up with a case where allowing the query from the expression inside
    	// exists() bleeds out into the surrounding query and cause an incorrect result,
    	// but Saxon tends to convert all these expressions:
        //  assertSearch  ("1", "count(/FM[exists(BLAH) eq false()])", 0, 1);
        //  assertSearch  ("1", "count(/FM[exists(BLAH) = false()])", 0, 1);
        // into this one, which is safe because the not() is treated as a non-optimized function,
        assertSearch  ("1", "count(/FM[not(exists(BLAH))])", 0, 1);
        
        // However this one exhibited the expected over-optimization failure:
    	assertSearch  ("1", "count(/FM[exists(BLAH) eq exists(BLARG)])", 0, 1);
    	assertSearch  ("1", "count(/FM[exists(BLAH) = exists(BLARG)])", 0, 1);
    	assertSearch  ("0", "count(/FM[exists(BLAH) != exists(BLARG)])", 0, 1);
    	assertSearch  ("0", "count(/FM[BLAH eq string(BLARG)])", 0, 0);
    	// NOTE: () eq () === ()
    	assertSearch  ("0", "count(/FM[BLAH eq BLARG])", 0, 0);
    	// NOTE: () = () === false()
    	assertSearch  ("0", "count(/FM[BLAH = BLARG])", 0, 0);

        // we don't optimize along the parent axis
        assertSearch  ("20", "count(//SCENE[not(exists(parent::ACT))])", 0, 26);

        assertSearch  ("1", "count(exists(/BLAH))", 0, 0);
    }


    @Test public void testLuxCount () throws Exception {
        assertSearch ("5", "lux:count('lux_path:\"\\{\\} ACT\"')", null, 5, 0);
    }

    @Test
    public void testPathOrder () throws Exception {
        // Make sure that the Optimizer doesn't incorrectly assert 
        // order is *not* significant in the generated query; 
        // it should be (SCENE AND ACT), and the query is *not* countable:
        // it's *not* how many documents have scenes and acts: it's how many scenes are there in documents with acts
        
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
        XdmResultSet results = assertSearch ("/ACT", QUERY_EXACT);
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
        XdmResultSet results = assertSearch("/ACT/SCENE", QUERY_MINIMAL);
        assertEquals (index.elementCounts.get("SCENE") + 0, results.size());
    }
    
    @Test
    public void testSearchAllScenes() throws Exception {
        XdmResultSet results = assertSearch("/ACT", QUERY_MINIMAL);
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
        XdmResultSet results = assertSearch("(/)[.//SCENE]", QUERY_EXACT);
        // every SCENE, in its ACT and in the PLAY
        assertEquals (index.elementCounts.get("SCENE") + index.elementCounts.get("ACT") + 1, results.size());
    }
    
    @Test
    public void testSearchAllSceneDocsRoot() throws Exception {
        XdmResultSet results = assertSearch("//SCENE/root()", QUERY_EXACT);
        // every SCENE, in its ACT and in the PLAY
        assertEquals (index.elementCounts.get("SCENE") + index.elementCounts.get("ACT") + 1, results.size());
    }
    
    @Test
    public void testCountDocs () throws Exception {
        // every SCENE, in its ACT and in the PLAY
        int sceneDocCount = index.elementCounts.get("SCENE") + index.elementCounts.get("ACT") + 1;

        XdmResultSet results = assertSearch("count (//SCENE/root())", QUERY_NO_DOCS);
        assertResultValue(results, sceneDocCount);
        
        results = assertSearch("count ((/)[.//SCENE])", QUERY_NO_DOCS);
        assertResultValue(results, sceneDocCount);

        results = assertSearch("count (//SCENE/ancestor::document-node())", QUERY_NO_DOCS);
        assertResultValue(results, sceneDocCount);

        results = assertSearch("count (/descendant-or-self::SCENE/root())", QUERY_NO_DOCS);
        assertResultValue(results, sceneDocCount);
        
        results = assertSearch("count (/descendant::SCENE/root())", QUERY_NO_DOCS);
        assertResultValue(results, sceneDocCount);
        
        results = assertSearch("count (/SCENE)", QUERY_NO_DOCS);
        assertResultValue(results, index.elementCounts.get("SCENE"));
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
    public void testTextComparison () throws Exception {
        long t = System.currentTimeMillis();
        String xpath = "//SCNDESCR >= //PERSONA";
        Evaluator eval = index.makeEvaluator();
        Compiler compiler = eval.getCompiler();
        XQueryExecutable xquery = compiler.compile(xpath);
        XdmResultSet results = eval.evaluate(xquery);
        System.out.println ("query evaluated in " + (System.currentTimeMillis() - t) + " msec,  retrieved " + results.size() + " result");
        AbstractExpression aex = compiler.makeTranslator().queryFor(xquery).getBody();
        aex = new UnOptimizer(index.indexer.getConfiguration()).unoptimize(aex);
        XQueryExecutable baseline = compiler.compile(aex.toString());
        XdmResultSet baseResult = eval.evaluate(baseline);
        assertEquals ("result count mismatch for: " + xquery.toString(), baseResult.size(), results.size());        
    }
    
    @Test
    public void testComparisonPredicate () throws Exception {
        long t = System.currentTimeMillis();
        String xpath = "//SCNDESCR[. >= //PERSONA]";
        Evaluator eval = index.makeEvaluator();
        Compiler compiler = eval.getCompiler();
        XQueryExecutable xquery = compiler.compile(xpath);
        XdmResultSet results = eval.evaluate(xquery);
        System.out.println ("query evaluated in " + (System.currentTimeMillis() - t) + " msec,  retrieved " + results.size() + " results");
        XQuery optimized = eval.getCompiler().makeTranslator().queryFor(xquery);
        XQuery unoptimized = new UnOptimizer(index.indexer.getConfiguration()).unoptimize(optimized);
        XQueryExecutable baseline = compiler.compile(unoptimized.toString());
        XdmResultSet baseResult = eval.evaluate(baseline);
        assertEquals ("result count mismatch for: " + xpath, baseResult.size(), results.size());
    }
    
    @Test
    public void testConstantExpression() throws Exception {
        // This resolves to a constant (Literal=true()) XPath expression and generates
        // a null Lucene query.  Make sure we don't try to execute the query.
        XdmResultSet results = assertSearch("'remorseless' or descendant::text", QUERY_CONSTANT);
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
        assertSearch ("HAMLET", "subsequence(/SCENE, 4, 1)/SPEECH[1]/SPEAKER/string()", null, 1);
    }
    
    @Test
    public void testSkipDocs () throws Exception {
        // Earlier implementations failed to indicate that the returned sequence of documents is sorted in document
        // order, causing Saxon to pull the entire result sequence.
        assertSearch ("KING CLAUDIUS", "subsequence((/)[.//SCENE], 4, 1)//SPEECH[1]/SPEAKER/string()", null, 1);
        assertSearch ("BERNARDO", "(//SCENE/SPEECH)[1]/SPEAKER/string()", null, 1);
    }
    
    @Test
    public void testSkipDocs2 () throws Exception {
        assertSearch ("BERNARDO", "(//SCENE/SPEECH)[1]/SPEAKER/string()", null, 1);
    }
    
    @Test
    public void testRoot () throws Exception {
        assertSearch ("KING CLAUDIUS", "(//SCENE/root())[4]//SPEECH[1]/SPEAKER/string()", null, 1);
        assertSearch ("KING CLAUDIUS", "subsequence(//SCENE/root(), 4, 1)//SPEECH[1]/SPEAKER/string()", null, 1);        
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
        assertSearch ("2", "count(/SPEECH[contains(., 'philosophy')])", null, 2);
        // TODO - why is this 141 and not 28?
        // FIXME - sometimes it *is* 28???
        assertSearch ("28", "count(/SPEECH[contains(., 'Horatio')])", null, 141);
        assertSearch ("8", "count(//SPEECH[contains(., 'philosophy')])", null, 7);
        // saxon cleverly optimizes this and gets rid of the intersect
        assertSearch ("1", "count(/SPEECH[contains(., 'philosophy')] intersect /SPEECH[contains(., 'Horatio')])", null, 1);
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
    public void testReversePaths () throws Exception {
        // expresses a deepish path in reverse order, using predicates
        assertSearch ("Where is your son?", "string(//LINE[3]" +
                "[parent::SPEECH[not(preceding-sibling::SPEECH)]]" +
                "[ancestor::SCENE[count(preceding-sibling::SCENE)=0]]" +
                "[ancestor::ACT[count(preceding-sibling::ACT)=3]]" +
                "[ancestor::PLAY])", null, 1);
        assertSearch ("Where is your son?", "string(//ACT[4]/SCENE[1]/SPEECH[1]/LINE[3]" +
        		"[../../../../self::PLAY[.. is root()]])", null, 1);
    }

    @Test
    public void testElementFullTextPhrase () throws Exception {
        // test phrase query generation
        // also handling of capitalization and tokenization (w/punctuation)
        assertSearch ("5", "count(//LINE[.='Holla! Bernardo!'])", null, 5, 5);
        assertSearch ("0", "count(//LINE[.='Holla!'])", null, 5, 5);
        assertSearch ("0", "count(//LINE[.='Holla Bernardo'])", null, 5, 5);
        // We cannot optimize this one due to the function call around (.):
        assertSearch ("1", "count(/ACT//LINE[lower-case(.)='holla! bernardo!'])", null, 5, 5);
        // ensure that paths ending in Dot don't accidentally reference the outer context
        assertSearch ("0", "count(/ACT//LINE[FOO//.='holla! bernardo!'])", null, 0, 0);
        // check stop word handling
        assertSearch ("<LINE>Where is your son?</LINE>", "//LINE[.='Where is your son?']", null, 5, 5);
    }
    
    @Test public void testFullText () throws Exception {
        assertSearch ("Where is your son?", "//*[.='Where is your son?']/string()", null, 5, 5);
    }
    
    @Test public void testContains () throws Exception {
        assertSearch ("5", "count(//LINE[contains(.,'Holla')])", null, 5, 5);
        assertSearch ("true", "contains(/PLAY,'Holla')", null, 1, 1);
        // searches match all 10 instances of 'given' since they will be case-insensitive
        // There is also one occurrence of 'forgiveness' that should match
        assertSearch ("1", "count (/LINE[contains(.,'Given')])", null, 11, 11);
        assertSearch ("10", "count (/LINE[contains(.,'given')])", null, 11, 11);
        int lineCount = index.elementCounts.get("LINE");
        // has to check every /LINE document:
        assertSearch ("1", "count(/LINE[contains(.,'olla! Bern')])", null, lineCount, lineCount);        
    }
    
    @Test public void testLuxSearch () throws Exception {
        assertSearch ("5", "count(lux:search('\"holla bernardo\"'))", null, 5, 0);
        assertSearch ("5", "count(lux:search('<:\"holla bernardo\"'))", null, 5, 0);
        assertSearch ("5", "count(lux:search('<LINE:\"holla bernardo\"'))", null, 5, 0);
        try {
            assertSearch (null, "lux:search(1,2,3)", null, null, null);
            assertTrue ("expected exception not thrown", false);
        } catch (LuxException e) { }
        try {
            assertSearch (null, "lux:search(1,2)", null, null, null);
            assertTrue ("expected exception not thrown", false);
        } catch (LuxException e) { }
        try {
            assertSearch (null, "lux:search(':::')", null, null, null);
            assertTrue ("expected exception not thrown", false);
        } catch (LuxException e) { 
            assertTrue (e.getMessage(), e.getMessage().startsWith("Cannot parse ':::'"));
        }
        assertSearch ("65", "lux:count(text{'bernardo'})", null, 65, 0);
    }
    
    @Test 
    public void testLuxSearchPath () throws Exception {
        assertSearch ("1", "count(lux:search('\"holla bernardo\"')/SPEECH)", null, 5, 5);   
    }
    
    /* Bug found in the wild - the opto that preserves document-ordering by embedding the trailing path 
     * in a predicate applied an incorrect query. */
    @Test
    public void testLuxSearchRoot () throws Exception {
        // This is the actual bug:
        String query = "lux:search (\"<@scene:5\")[1]/root()";
        assertSearch ("__IGNORE__", query, null, 1, 1);
        // Some attempts to reproduce, kept for posterity?
        // first result is LINE due to TFIDF (relevance) scoring
        assertSearch ("LINE", "lux:search('\"holla bernardo\"')[1]/root()/*/name()", null, 1, 1);
        assertSearch (null, "lux:search('<@id:100')[1]/root()/*/name()", null, 0, 0);
    }
    
    @Test
    public void testBugFix0018() throws Exception {
        assertSearch ("MARCELLUS", "for $doc in /SPEECH[LINE='Holla! Bernardo!'] return $doc/SPEAKER/string()", null, 1, 1);
    }
    
    @Test
    public void testEmptyReturn() throws Exception {
        assertSearch (null, "for $doc in /SPEECH[LINE='Holla! Bernardo!'] return $doc/UNKNOWN/string()", null, 0, 0);
    }
    
    @Test 
    public void testBugFix0018b() throws Exception {
        assertSearch (HAMLET_TITLE_MARKUP, "lux:search(\"*:*\")[2]", null, 1, 1);
    }

    @Test 
    public void testTrailingStringCall () throws Exception {
        assertSearch ("Where is your son?", "/PLAY/ACT[4]/SCENE[1]/SPEECH[1]/LINE[3]/string()", null, 1);        
    }
    
    @Test
    public void testOrderBy () throws Exception {
        // TODO: we don't yet have a solution that allows us to push the order by
        // optimization (to say nothing of additional constraints) into a user-supplied
        // query using the string query syntax.
        assertSearch ("ACT", "(for $doc in lux:search('bernardo')" + 
            " order by lux:key('doctype', $doc) return $doc/*/name())[1]", 0, 1);
    }
        
    @Test
    public void testOrderByPagination () throws Exception {
        assertSearch ("SPEAKER", "(for $doc in lux:search('bernardo')" + 
            " order by lux:key('doctype', $doc) return $doc/*/name())[21]", 0, 1);
        assertSearch ("<SPEAKER>BERNARDO</SPEAKER>", "(for $doc in lux:search('bernardo')" + 
                " order by lux:key('doctype', $doc) return $doc)[21]", 0, 1);
    }
    
    @Test
    public void testHighlight () throws Exception {
        assertSearch ("<TITLE>The Tragedy of <B>Hamlet</B>, Prince of Denmark</TITLE>",
                      "lux:highlight(/PLAY/TITLE, 'hamlet')", null, null);
    }

    @Test
    public void testHighlightMultiple () throws Exception {
        assertSearch ("<TITLE>The <B>Tragedy</B> <B>of</B> <B>Hamlet</B>, Prince <B>of</B> Denmark</TITLE>", 
                      "lux:highlight(/PLAY/TITLE,'tragedy of hamlet')", null, null);
    }

    @Test
    public void testHighlightPhrase () throws Exception {
        assertSearch ("<TITLE>The <B>Tragedy</B> <B>of</B> <B>Hamlet</B>, Prince of Denmark</TITLE>",
                      "lux:highlight(/PLAY/TITLE, '\"tragedy of hamlet\"')", null, null);
    }
    
    @Test
    public void testHighlightElementQuery () throws Exception {
        assertSearch ("<TITLE>The Tragedy of <B>Hamlet</B>, Prince of Denmark</TITLE>", 
                "lux:highlight(/PLAY/TITLE, '<TITLE:hamlet')", null, null);
    }
    
    @Test
    public void testHighlightElementMultiple () throws Exception {
        assertSearch ("<TITLE>The <B>Tragedy</B> of <B>Hamlet</B>, Prince of Denmark</TITLE>", 
                "lux:highlight(/PLAY/TITLE, '<TITLE:hamlet <TITLE:tragedy')", null, null);
    }
    
    // Highlighting element-phrase-queries is not well-supported by the current highlighter.
    // because the Lucene phrase highlighting is restricted to operate on a single field,
    // and we use the main text field.  So we choose to err on thse side of over-highlighting
    // using a workaround that ignores element restrictions in the presence of phrase queries.
    @Test
    public void testHighlightElementPhrase () throws Exception {
        assertSearch ("<TITLE>The <B>Tragedy</B> <B>of</B> <B>Hamlet</B>, Prince of Denmark</TITLE>", 
                "lux:highlight(/PLAY/TITLE, '<TITLE:\"tragedy of hamlet\"')", null, null);
    }
    
    @Test 
    public void testHighlightMixedQuery () throws Exception {
        assertSearch ("<TITLE>The <B>Tragedy</B> <B>of</B> <B>Hamlet</B>, Prince of Denmark</TITLE>",
                "lux:highlight(/PLAY/TITLE, '<TITLE:tragedy \"of hamlet\"')", null, null);
    }
    
    @Test
    public void testHighlightAttributeQuery () throws Exception {
        // no highlighting in attributes
        assertSearch ("<node id=\"10\">node 10</node>", "lux:highlight(<node id=\"10\">node 10</node>, '<@id:10')", null, null);
    }
    
    // Make sure text offset calculations handle multiple text nodes
    @Test
    public void testHighlightComplexContent() throws Exception {
        assertSearch ("<FM>\n<P>Text placed in the public domain by Moby Lexical Tools, 1992.</P>\n" +
        		"<P>SGML markup by <B>Jon</B> <B>Bosak</B>, 1992-1994.</P>\n" +
        		"<P>XML version by <B>Jon</B> <B>Bosak</B>, 1996-1998.</P>\n" +
        		"<P>This work may be freely copied and distributed worldwide.</P>\n</FM>", 
                "lux:highlight(/FM, 'Jon Bosak')", null, null);
    }
    
    @Test 
    public void testFunctionDefinition() throws Exception {
        // test the optimization of function bodies, and also of the collection()
        // function call.  collection() must be used inside function bodies since 
        // Saxon generates static (compilation) errors due to the lack of a context item 
        // for the absolute paths 
        assertSearch (HAMLET_TITLE_MARKUP,
            "declare namespace test='test'; " +
            "declare function test:function () { collection()/PLAY/TITLE }; " +
            "test:function()", null, 1);
    }
    
    @Test
    public void testFlworPath() throws Exception {
        // test that a let clause that doesn't match any data doesn't prevent the
        // entire query from matching
        assertSearch (HAMLET_TITLE_MARKUP, "let $play := collection()/PLAY[TITLE]\n" +
        		"return ($play/TITLE)", null, 1);
    }
    
    @Test
    public void testSequence() throws Exception {
        // test that queries generated by a sequence combine properly (ie using OR)
        // if $play = () then we should get back ''.
        assertSearch (HAMLET_TITLE, "let $play := collection()/PLAY[TITLE]\n" +
                "return (string($play/TITLE), $play/@id)", null, 1);
    }
    
    @Test
    public void testLet() throws Exception {
        assertSearch (HAMLET_TITLE, "let $play := collection()/PLAY[TITLE]\n" +
                "let $id := $play/@id\n" +
                "return (string($play/TITLE), $id)", null, 1);
    }

    @Test
    public void testIrrelevantLet() throws Exception {
        // test that a let clause that doesn't match any data doesn't prevent the
        // entire query from matching.  This is hard to do b/c Saxon tends
        // to optimize away so much.  This also provides an opportunity to ensure
        // that we optimize through the use of variables, since the variables
        // are preserved in this expression, even after Saxon's optos.
        assertSearch (HAMLET_TITLE, "let $play := collection()/PLAY[starts-with(TITLE,'The ')]\n" +
                "let $id := $play/@id\n" +
                "return <result id='{$id}'>{if ($id) then '' else $play/TITLE}</result>/string()", null, 1);
    }
    
    @Test
    public void testDeepPagination () throws Exception {
    	//ensure that deep pagination skips intervening documents without loading them into memory
    	assertSearch ("1", "count(collection()[1000]/*)", null, 1);
    }
    
    @Test
    public void testWhereAtClause () throws Exception {
        // return the index of the first /SCENE document ; the first SCENE is the 44th element in hamlet.xml,
        // and therefore the root of document #44 in the test set
        String query = "(for $doc at $i in collection() where $doc/SCENE return $i)[1]";
        assertSearch ("44", query, null, 44);
    }
    
    @Test
    public void testFieldValuesComparison () throws Exception {
    	String query = "collection()[lux:key('doctype')='SCENE'][1]/descendant::SPEECH[1]/SPEAKER/string()";
    	// there are 20 scenes in Hamlet, but we only need to pull the first one for this query
    	assertSearch ("BERNARDO", query, null, 1, 1);
        
    	query = "collection()[lux:key('doctype')='SCENE'][1]/descendant::SPEAKER[1]/string()";
        assertSearch ("BERNARDO", query, null, 1, 1);

        query = "count(collection()[lux:key('doctype')='SCENE'])";
    	assertSearch ("20", query, null, 20, 0);
    }

    @Test
    public void testPredicateChain() throws Exception {
    	String query = "count(//ACT[1]/SCENE[2]/SPEECH[3]/SPEAKER)";
    	assertSearch ("6", query, null, 6, 6);
    }
    
    @Test
    public void testNestedPredicateComparison() throws Exception {
        String query = "exists(/PLAY[ACT[SCENE/TITLE='SCENE IV.  The platform.']])";
    	assertSearch ("true", query, null, 1, 1);
    }
    
    @Test
    public void testRangeInequality() throws Exception {
        // we have five ACTs 
        String query = "count((/)[lux:key('doctype') <= 'ACT'])";
        assertSearch ("5", query, null, 5, 0);
        query = "count((/)[lux:key('doctype') le 'ACT'])";
        assertSearch ("5", query, null, 5, 0);
    }

    @Test
    public void testCombinedRange() throws Exception {
        String query = "count((/)[lux:key('doctype') >= 'A'][lux:key('doctype') <= 'B'])";
        assertSearch ("5", query, null, 5, 0);

        // we have one FM and two GRPDESCR
        query = "count((/)[lux:key('doctype') > 'F'][lux:key('doctype') < 'H'])";
        assertSearch ("3", query, null, 3, 0);
        query = "count((/)[lux:key('doctype') gt 'F' and lux:key('doctype') lt 'H'])";
        assertSearch ("3", query, null, 3, 0);
    }
    
    @Test
    public void testRangeGenComp() throws Exception {
        // we have only one SCNDESCR, but we don't optimize this yet
        String query = "count((/)[lux:key('doctype') gt 'S' and lux:key('doctype') lt 'T' " +
        		"and not(lux:key('doctype') = ('SCENE','SPEECH','SPEAKER','STAGEDIR'))])";
        assertSearch ("1", query, null, 2552, 2552);
    }
    
    @Test
    public void testFieldValuesNoContext () throws Exception {
    	// compare an integer against a string-valued field
    	String query = "if (2 eq lux:key('xxx')) then 'yes' else 'no'";
        try {
            assertSearch ("false", query, null, 0, 0);
        	fail ("expected exception not thrown");
        } catch (LuxException e) {
        	assertTrue (e.getMessage().contains("no context defined"));
        }
    }
    
    @Test
    public void testIntFieldEquality() throws Exception {
    	String query;
    	// check that our int-valued field was indexed correctly:
    	query = "(/ACT)[2]/lux:key('actnum')";
    	assertSearch ("2", query, null, 1, 1);
    	// do a basic int comparison
    	query = "count(collection()[2 eq lux:key('actnum')])";
    	assertSearch ("3", query, null, 3, 0);
    	// Try comparing an integer against a string-valued field
    	query = "count(collection()[2 eq lux:key('actstr')])";
    	try {
    		assertSearch ("0", query, null, 0, 0);
    		fail ("expected exception not thrown");
    	} catch (LuxException e) {
    		assertEquals("Cannot compare xs:integer to xs:string", e.getMessage());
    	}
    }

    @Test
    public void testIntFieldInequality() throws Exception {
    	String query;
    	// do a basic int comparison
    	query = "count(collection()[lux:key('actnum') lt 2])";
    	assertSearch ("6", query, null, 6, 0);
    	query = "count(collection()[lux:key('actnum') < 2])";
    	assertSearch ("6", query, null, 6, 0);
    	query = "count(collection()[lux:key('actnum') > 2][lux:key('actnum') <= 3])";
    	assertSearch ("5", query, null, 5, 0);
    }

    @Test
    public void testLongFieldInequality() throws Exception {
    	// do a basic long comparison, and make sure comparison with other numeric types is allowed
    	String query = "count(collection()[lux:key('scnlong') gt xs:int(5)])";
    	assertSearch ("2", query, null, 2, 0);
    }
    
    @Test @Ignore
    public void testXPathRangeQuery () throws Exception {
    	String query;
    	// do a basic integer comparison; the cast is required for atomic comparison
    	query = "count(//SCENE[xs:integer(@act) lt 2])";
    	assertSearch ("8", query, null, 6, 6);
    	query = "count(//SCENE[@act < 2])";
    	assertSearch ("6", query, null, 4, 4);
    	query = "count(//SCENE[xs:integer(@act) > 2][xs:integer(@act) <= 3])";
    	assertSearch ("9", query, null, 7, 7);
    }

    @Test
    public void testAttributePredicate() throws Exception {
    	// from Geet Gangwar
    	//context /@id[.='I2009']
    	String query = "count(//SCENE/@act[.='2'])";
    	assertSearch ("6", query, null, 4, 4);
    	query = "//SCENE/@act[.='2']";
    	XdmResultSet results = assertSearch (query, (Integer) null, 4, 4);
    	assertEquals (6, results.getXdmValue().size());
    }
    
    /* See LUX-62 */
    @Test @Ignore
    public void testTimestampRange () throws Exception {
        String query = "lux:count('timestamp:[2013-09-01T21:30:50.515Z TO NOW]')";
        assertSearch ("6636", query, null, 0, 0);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
