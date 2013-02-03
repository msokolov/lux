package lux;

import static lux.compiler.XPathQuery.BOOLEAN_FALSE;
import static lux.compiler.XPathQuery.BOOLEAN_TRUE;
import static lux.compiler.XPathQuery.DOCUMENT_RESULTS;
import static lux.compiler.XPathQuery.MINIMAL;
import static lux.compiler.XPathQuery.SINGULAR;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;

import lux.compiler.XPathQuery;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.xml.ValueType;
import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitorBase;
import lux.xpath.FunCall;
import lux.xpath.LiteralExpression;
import lux.xquery.XQuery;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests the parsing of XPath expressions and the generation
 * of a supporting Lucene query using node name indexes, and using path indexes.
 */
@RunWith(MultiThreadedRunner.class)
public class BasicQueryTest {
    
    public enum Q {
        ATTR, SCENE, SCENE_ACT, 
            ACT, ACT1, ACT2, ACT_CONTENT, ACT_CONTENT1, ACT_SCENE, ACT_SCENE1, ACT_SCENE_CONTENT, ACT_SCENE_CONTENT1, ACT_SCENE_SPEECH, ACT_OR_SCENE, 
            ACT_ID, ACT_ID_123, ACT_SCENE_ID_123,
            MATCH_ALL, ACT_SCENE2, ACT_AND_SCENE, ACT_SCENE3, AND, PLAY_ACT_OR_PERSONAE_TITLE, 
            LUX_FOO, LINE, TITLE, ACT_SCENE_SPEECH_AND, 
    };
    
    protected Compiler compiler;
    protected Evaluator eval;
    
    @Before public void setup () {
        compiler = new Compiler(getIndexer().getConfiguration());
        eval = new Evaluator(compiler, null, null);
    }
    
    @Test public void testNoQuery () throws Exception {

        try {
            assertQuery ("", MINIMAL, ValueType.DOCUMENT);
            assertFalse ("expected syntax error", true);
        } catch (Exception e) { }

        try {
            assertQuery (null, MINIMAL, ValueType.DOCUMENT);
            assertFalse ("expected NPE", true);
        } catch (NullPointerException e) {}
    }
    
    @Test public void testParseError () throws Exception {
        try {
            assertQuery ("bad xpath here", 0);
            assertFalse ("expected syntax error", true);
        } catch (Exception ex) { }
    }

    @Test public void testMatchAll () throws Exception {
        // Can you have an XML document with no elements?  I don't think so
        //assertQuery ("*", Q.MATCH_ALL, true, ValueType.ELEMENT);
        //assertQuery ("node()", Q.MATCH_ALL, true, ValueType.NODE);
        assertQuery ("/*", MINIMAL|SINGULAR, ValueType.ELEMENT, Q.MATCH_ALL);
        assertQuery ("/node()", MINIMAL, ValueType.NODE, Q.MATCH_ALL); // can have multiple root nodes
        //assertQuery ("self::node()", Q.MATCH_ALL, MINIMAL);
    }
    
    @Test public void testSlash() throws Exception {
        assertQuery ("/", MINIMAL|SINGULAR|DOCUMENT_RESULTS, ValueType.DOCUMENT, Q.MATCH_ALL);
    }
    
    @Test public void testElementPredicate() throws Exception {
        assertQuery ("(/)[.//ACT]", MINIMAL|DOCUMENT_RESULTS|SINGULAR, ValueType.DOCUMENT, Q.ACT); 
    }

    @Test public void testElementPaths () throws Exception {

        int facts = hasPathIndexes() ? MINIMAL : 0;

        assertQuery ("//ACT", MINIMAL, ValueType.ELEMENT, Q.ACT);

        assertQuery ("/*/ACT", facts, ValueType.ELEMENT, Q.ACT2);
        
        assertQuery ("/ACT//*", 0, ValueType.ELEMENT, Q.ACT1);

        facts = hasPathIndexes() ? MINIMAL|SINGULAR : 0;
        assertQuery ("/ACT", facts, ValueType.ELEMENT, Q.ACT1);
        
        // this should be ValueType.TEXT shouldn't it??
        assertQuery ("/ACT/text()", 0, ValueType.ELEMENT, Q.ACT1);

        assertQuery ("//*/@attr", MINIMAL, ValueType.ATTRIBUTE, Q.ATTR);

        assertQuery ("//node()/@attr", MINIMAL, ValueType.ATTRIBUTE, Q.ATTR);
    }

    @Test
    public void testConvertRootedPathToPredicate() throws Exception {
        int facts = hasPathIndexes() ? DOCUMENT_RESULTS|SINGULAR|MINIMAL : DOCUMENT_RESULTS|SINGULAR;
        assertQuery ("//ACT/SCENE/root()", "lux:search(" + 
                     getQueryXml(Q.ACT_SCENE) + "," + facts + ")" +
        		"[(descendant::element(ACT)/child::element(SCENE))/root(.)]", 
        		facts, ValueType.DOCUMENT, Q.ACT_SCENE);
    }    
    
    @Test public void testAttributePredicates () throws Exception {
        assertQuery ("//*[@attr]", MINIMAL, ValueType.ELEMENT, Q.ATTR);

        assertQuery ("(/)[.//*/@attr]", MINIMAL|SINGULAR|DOCUMENT_RESULTS, ValueType.DOCUMENT, Q.ATTR);        
    }

    @Test public void testElementAttributePaths () throws Exception {
        
        assertQuery ("//ACT/@id", MINIMAL, ValueType.ATTRIBUTE, Q.ACT_ID);

        assertQuery ("//ACT/@*", 0, ValueType.ATTRIBUTE, Q.ACT);
    }

    @Test public void testTwoElementPaths () throws Exception {
        int facts = hasPathIndexes() ? MINIMAL : 0;
        assertQuery ("//*/ACT/SCENE", facts, ValueType.ELEMENT, Q.ACT_SCENE);
        assertQuery ("/ACT/SCENE", facts, ValueType.ELEMENT, Q.ACT_SCENE1);
        assertQuery ("/ACT//SCENE", facts, ValueType.ELEMENT, Q.ACT_SCENE2);
    }
    
    @Test public void testTwoElementPredicates () throws Exception {
        assertQuery ("(/)[.//ACT][.//SCENE]", MINIMAL|SINGULAR|DOCUMENT_RESULTS, ValueType.DOCUMENT, Q.ACT_SCENE3);
    }
    
    @Test public void testUnion () throws Exception {
        // can't execute without context item
        // assertQuery ("ACT|SCENE", 0, ValueType.ELEMENT);

        assertQuery ("//ACT|//SCENE", MINIMAL, ValueType.ELEMENT, Q.ACT_OR_SCENE);
    }

    @Test public void testPositionalPredicate () throws Exception {
        int facts = hasPathIndexes() ? MINIMAL : 0;
        assertQuery ("//ACT/SCENE[1]", facts, ValueType.ELEMENT, Q.ACT_SCENE);
        assertQuery ("/descendant-or-self::SCENE[1]", MINIMAL, ValueType.ELEMENT, Q.SCENE);
        assertQuery ("//SCENE[1]", MINIMAL, ValueType.ELEMENT, Q.SCENE);        
        assertQuery ("//SCENE[last()]", MINIMAL, ValueType.ELEMENT, Q.SCENE);

        // not minimal, since there may not be a second SCENE element
        assertQuery ("//SCENE[2]", 0, ValueType.ELEMENT, Q.SCENE);
        
        assertQuery ("(//ACT)[1]", MINIMAL, ValueType.ELEMENT, Q.ACT); 
    }
    
    @Test public void testSubsequence () throws Exception {
        assertQuery ("subsequence (//ACT, 1, 1)", MINIMAL, ValueType.ELEMENT, Q.ACT);
        // save this until we have implemented some kind of pagination in XPathQuery
        // lux:search and XPathCollector
        // assertQuery ("subsequence (//ACT, 2)", 0, Q.ACT);
        assertQuery ("subsequence (//ACT, 1, 10)", MINIMAL, ValueType.ELEMENT, Q.ACT);
        //assertQuery ("subsequence (//ACT, 10, 10)", 0, Q.ACT);
    }

    @Test public void testMultiElementPaths () throws Exception {
        int facts = hasPathIndexes() ? MINIMAL : 0;        
        assertQuery ("//ACT/TITLE | //SCENE/TITLE| //SPEECH/TITLE",
                     facts,
                     ValueType.ELEMENT, Q.ACT_SCENE_SPEECH);
        // This was three separate queries, whose results would then have to be merged together,
        // but our Optimizer declares all these expressions as ordered, enabling Saxon to merge them 
        // together into a single query
        assertQuery ("/PLAY/(ACT|PERSONAE)/TITLE", facts, ValueType.ELEMENT, Q.PLAY_ACT_OR_PERSONAE_TITLE);
    }
    
    @Test public void testBooleanSpanCombo() throws Exception {
        if (!hasPathIndexes()) {
            return;
        }
        int facts = hasPathIndexes() ? MINIMAL |SINGULAR | DOCUMENT_RESULTS : SINGULAR| DOCUMENT_RESULTS;        
        assertQuery ("//ACT/TITLE/root()//SCENE/TITLE/root()//SPEECH/TITLE/root()",
                     facts,
                     ValueType.DOCUMENT, Q.ACT_SCENE_SPEECH_AND);
    }
    
    @Test public void testManySmallDocs () throws Exception {
        int facts = hasPathIndexes() ? MINIMAL|SINGULAR : 0;
        assertQuery ("/LINE", facts, ValueType.ELEMENT, Q.LINE);        
    }

    @Test @Ignore public void testElementValueNoPath () throws Exception {
        // depends on context expression when there is none
        assertQuery ("ACT[.='content']", 0, ValueType.ELEMENT);
    }
    
    @Test public void testElementValue () throws Exception {
        int facts = hasPathIndexes() ? SINGULAR : 0;
        assertQuery ("/ACT[.='content']", facts, ValueType.ELEMENT, Q.ACT_CONTENT1);

        assertQuery ("/ACT[SCENE='content']", facts, ValueType.ELEMENT, Q.ACT_SCENE_CONTENT1);
        assertQuery ("/ACT['content'=SCENE]", facts, ValueType.ELEMENT, Q.ACT_SCENE_CONTENT1);
        assertQuery ("/ACT/SCENE[.='content']", 0, ValueType.ELEMENT, Q.ACT_SCENE_CONTENT1);

        assertQuery ("//ACT[.='content']", 0, ValueType.ELEMENT, Q.ACT_CONTENT);

        assertQuery ("//ACT[SCENE='content']", 0, ValueType.ELEMENT, Q.ACT_SCENE_CONTENT);
    }
    
    @Test public void testElementValueSelf() throws Exception {
        int facts = hasPathIndexes() ? SINGULAR : 0;
        //assertQuery ("/ACT/SCENE[self::node()='content']", 0, ValueType.ELEMENT, Q.ACT_SCENE_CONTENT1);        
        //assertQuery ("/*[self::ACT='content']", 0, ValueType.ELEMENT, Q.ACT_CONTENT1);
        assertQuery ("/*[self::ACT/SCENE='content']", facts, ValueType.ELEMENT, Q.ACT_SCENE_CONTENT1);
        assertQuery ("/*[self::ACT/SCENE/self::*='content']", facts, ValueType.ELEMENT, Q.ACT_SCENE_CONTENT1);
    }
    
    @Test public void testAttributeValue () throws Exception {
        int facts = hasPathIndexes() ? SINGULAR : 0;
        assertQuery ("/ACT[@id=123]", facts, ValueType.ELEMENT, Q.ACT_ID_123);

        assertQuery ("/ACT[SCENE/@id=123]", facts, ValueType.ELEMENT, Q.ACT_SCENE_ID_123);
    }

    @Test public void testAncestorOrSelf () throws Exception {
        assertQuery ("/ancestor-or-self::node()", MINIMAL|SINGULAR|DOCUMENT_RESULTS, ValueType.DOCUMENT, Q.MATCH_ALL);
    }
    
    @Test public void testSelf () throws Exception {
        assertQuery ("/self::node()", MINIMAL|SINGULAR|DOCUMENT_RESULTS, ValueType.DOCUMENT, Q.MATCH_ALL);
    }
    
    @Test public void testAtomicResult () throws Exception {
        int facts = hasPathIndexes() ? MINIMAL : 0;
        assertQuery ("number((/ACT/SCENE)[1])", facts, ValueType.ATOMIC, Q.ACT_SCENE1);
        assertQuery ("number((/descendant-or-self::ACT)[1])", MINIMAL, ValueType.ATOMIC, Q.ACT);
    }
    
    @Test public void testCounting () throws Exception {
        assertQuery ("count(/)", SINGULAR | MINIMAL, ValueType.ATOMIC, Q.MATCH_ALL);
        assertQuery ("count(//ACT)", MINIMAL, ValueType.ATOMIC, Q.ACT);
        assertQuery ("count(//ACT/root())", SINGULAR | MINIMAL, ValueType.ATOMIC, Q.ACT);
        assertQuery ("count(//ACT/ancestor::document-node())", SINGULAR | MINIMAL, ValueType.ATOMIC, Q.ACT);
        // FIXME: the optimizer should mark this as minimal/counting too now that we have path queries
        int facts = hasPathIndexes() ? SINGULAR | MINIMAL : SINGULAR | DOCUMENT_RESULTS;
        assertQuery ("count(//ACT/SCENE/ancestor::document-node())", facts, (ValueType)null, Q.ACT_SCENE);
    }
    
    @Test public void testCount2 () throws Exception {
        assertQuery ("count(//ACT/root()//SCENE)", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.ACT_AND_SCENE);
    }
    
    @Test public void testExistence() throws Exception {
        assertQuery ("exists(/)", MINIMAL|BOOLEAN_TRUE, ValueType.BOOLEAN, Q.MATCH_ALL);
        assertQuery ("exists(//ACT)", MINIMAL|BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT);
        assertQuery ("exists(//ACT/root())", MINIMAL|BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT);
        assertQuery ("exists(//ACT) and exists(//SCENE)", MINIMAL|BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT, Q.SCENE);
        assertQuery ("exists(//ACT/root()//SCENE)", MINIMAL|BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT_AND_SCENE);
        // TODO: merge queries such as this into one:
        // assertQuery ("exists(//ACT/root() intersect //SCENE/root())", MINIMAL, ValueType.BOOLEAN, Q.ACT_SCENE);
        assertQuery ("exists((/)[.//ACT and .//SCENE])", MINIMAL|BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT_AND_SCENE);
        int facts = hasPathIndexes() ? MINIMAL : 0;
        assertQuery ("//ACT[exists(SCENE)]", facts, ValueType.NODE, Q.ACT_SCENE);
    }
    
    @Test public void testNonexistence() throws Exception {
        assertQuery ("empty(/)", MINIMAL|BOOLEAN_FALSE, ValueType.BOOLEAN, Q.MATCH_ALL);
        assertQuery ("empty(//ACT)", MINIMAL|BOOLEAN_FALSE, ValueType.BOOLEAN, Q.ACT);
        assertQuery ("empty(//ACT/root())", MINIMAL|BOOLEAN_FALSE, ValueType.BOOLEAN, Q.ACT);
        assertQuery ("empty(//ACT) and empty(//SCENE)", MINIMAL|BOOLEAN_FALSE, ValueType.BOOLEAN, Q.ACT, Q.SCENE);
        assertQuery ("empty(//ACT/root()//SCENE)", MINIMAL|BOOLEAN_FALSE, ValueType.BOOLEAN, Q.ACT_AND_SCENE);
        assertQuery ("empty((/)[.//ACT and .//SCENE])", MINIMAL|BOOLEAN_FALSE, ValueType.BOOLEAN, Q.ACT_AND_SCENE);

        assertQuery ("//ACT[empty(SCENE)]", 0, ValueType.NODE, Q.ACT);
    }
    
    @Test public void testNot() throws Exception {
        assertQuery ("not(/)", MINIMAL|BOOLEAN_TRUE, ValueType.BOOLEAN, Q.MATCH_ALL);
        assertQuery ("not(//ACT)", MINIMAL|BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT);
        assertQuery ("not(//ACT/root())", MINIMAL|BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT);
        assertQuery ("not(//ACT) and empty(//SCENE)", MINIMAL|BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT, Q.SCENE);
        assertQuery ("not(//ACT/root()//SCENE)", MINIMAL|BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT_AND_SCENE);
        assertQuery ("not((/)[.//ACT and .//SCENE])", MINIMAL|BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT_AND_SCENE);
    }
    @Test public void testPredicateNegation () throws Exception {
        assertQuery ("//ACT[not(SCENE)]", 0, ValueType.ELEMENT, Q.ACT);
        assertQuery ("//ACT[count(SCENE) = 0]", 0, ValueType.ELEMENT, Q.ACT);
    }
    
    @Test public void testPredicateCombine () throws Exception {
        // This should depend on having both ACT and SCENE
        assertQuery ("//ACT[.//SCENE]", MINIMAL, ValueType.ELEMENT, Q.ACT_SCENE3);
        assertQuery ("//ACT[exists(.//SCENE)]", MINIMAL, ValueType.ELEMENT, Q.ACT_SCENE3);        
        // TODO: optimize not() expressions involving exists() and empty()
        // This should depend on having a SCENE!
        assertQuery ("//ACT[not(empty(.//SCENE))]", 0, ValueType.ELEMENT, Q.ACT); 
    }
    
    @Test public void testReservedWords () throws Exception {
        // internally, we use certain words to denote search operations.  Make sure these are not
        // confused with query terms
        assertQuery("//AND", MINIMAL, ValueType.ELEMENT, Q.AND);        
    }

    @Test public void testNamespaceAware () throws Exception {
        assertQuery("//lux:foo", MINIMAL, ValueType.ELEMENT, Q.LUX_FOO);
    }
    
    @Test public void testCollection () throws Exception {
        // fn:collection() is implicit
        assertQuery ("collection()//SCENE", "lux:search(" +
                getQueryXml(Q.SCENE)
                + ",2)/descendant::element(SCENE)", MINIMAL, ValueType.ELEMENT, Q.SCENE);
    }
    
    @Test public void testOrderBy () throws Exception {
        String query = "for $doc in //ACT order by $doc/lux:field-values('sortkey') return $doc";
        assertQuery (query, MINIMAL, ValueType.ELEMENT, Q.ACT);
        assertSortKeys (query, "sortkey");
    }
    
    @Test public void testOrderByContextArgument () throws Exception {
        String query = "for $doc in //ACT order by lux:field-values('sortkey', $doc) return $doc";
        assertQuery (query, MINIMAL, ValueType.ELEMENT, Q.ACT);
        assertSortKeys (query, "sortkey");
    }

    @Test public void testOrderBySearchFunCall () throws Exception {
        String query = "for $doc in lux:search('foo') order by lux:field-values('sortkey', $doc) return $doc";
        assertQuery (query, null, MINIMAL, ValueType.VALUE, "foo");
        assertSortKeys (query, "sortkey");
    }
    @Test 
    public void testOrderBy2Keys () throws Exception {
        // two indexed sortkeys
        String query = "for $doc in //ACT order by $doc/lux:field-values('sortkey'), $doc/lux:field-values('sk2') return $doc";
        assertQuery (query, MINIMAL, ValueType.ELEMENT, Q.ACT);
        assertSortKeys (query, "sortkey,sk2");
    }
    
    @Test 
    public void testOrderByDescending () throws Exception {
        // two indexed sortkeys, plus descending
        String query = "for $doc in //ACT order by $doc/lux:field-values('sortkey') descending, $doc/lux:field-values('sk2') return $doc";
        assertSortKeys (query, "sortkey descending,sk2");
    }
    
    @Test
    public void testOrderByNoIndex () throws Exception {
        // one indexed sortkey, one xpath sort
        String query = "for $doc in //ACT order by $doc/lux:field-values('sortkey'), $doc/date descending return $doc";
        assertSortKeys (query, "sortkey");

        // one xpath sort, one indexed sortkey
        query = "for $doc in //ACT order by $doc/date, $doc/lux:field-values('sortkey') ascending return $doc";
        assertSortKeys (query, new String[0]);
    }
    
    // This test ensures that the optimizer ignores the argument of string(), and does not 
    // use it to limit the set of documents evaluated by the query
    @Test
    public void testAtomizingEmptySequence () throws Exception {
        String query = "(for $doc in collection() return string ($doc/*/TITLE))[2]";
        // should return the titles of the second document in document order (which is a TITLE 
        // and has no TITLE), but this was failing because we fetched only documents containing TITLE
        assertQuery (query, SINGULAR|DOCUMENT_RESULTS, Q.MATCH_ALL);

        query = "(for $doc in collection() return data($doc//TITLE))[2]";
        assertQuery (query, MINIMAL, Q.TITLE);

    }
    
    public void assertQuery (String xpath, Integer facts, Q ... queries) throws Exception {
        assertQuery (xpath, facts, null, queries);
    }

    public void assertQuery (String xpath, Integer facts, ValueType type, Q ... queries) throws Exception{
        assertQuery (xpath, null, facts, type, queries);
    }
    
    /**
     * asserts that the given xpath generates the lucene query string, and 
     * that the asserted facts are passed to lux:search
     * 
     * @param xpath the expression to be tested
     * @param expectedOptimized the expected optimized query expression
     * @param facts facts expected for the first query
     * @param type return type expected for the first query
     * @param queries the expected lucene query strings
     * @throws Exception 
     */

    public void assertQuery (String xpath, String expectedOptimized, Integer facts, ValueType type, Q ... queries) throws Exception {
        String[] qs = new String[queries.length];
        int i = 0;
        for (Q q : queries) {
            qs[i++] = getQueryXml(q);
        }
        assertQuery (xpath, expectedOptimized, facts, type, qs);
    }

    private void assertSortKeys(String xpath, String ... sortFields) {
        compiler.compile(xpath);
        XQuery optimizedQuery = compiler.getLastOptimized();
        AbstractExpression ex = optimizedQuery.getBody();
        SortExtractor extractor = new SortExtractor();
        ex.accept(extractor);
        assertEquals ("incorrect number of sort fields:", sortFields.length, extractor.sorts.size());
        for (int i = 0; i < sortFields.length; i++) {
            assertEquals (sortFields[i], extractor.sorts.get(i));
        }
    }

    private void assertQuery(String xpath, String expectedOptimized, int facts, ValueType type, 
            String ... queries) 
    {
        compiler.compile(xpath);
        XQuery optimizedQuery = compiler.getLastOptimized();
        AbstractExpression ex = optimizedQuery.getBody();
        if (expectedOptimized != null) {
            assertEquals (expectedOptimized, optimizedQuery.toString());
        }
        SearchExtractor extractor = new SearchExtractor();
        ex.accept(extractor);
        assertEquals ("wrong number of queries for " + xpath, queries.length, extractor.queries.size());
        for (int i = 0; i < queries.length; i++) {
            assertEquals (queries[i], extractor.queries.get(i).toString());
            //assertEquals (getQueryString(queries[i]), extractor.queries.get(i).toString());
        }
        if (queries.length > 0) {
            boolean isMinimal = (facts & MINIMAL) != 0;
            assertEquals ("isMinimal was not " + isMinimal + " for xpath " + xpath,
                    isMinimal, extractor.queries.get(0).isFact(MINIMAL));
            assertEquals ("unexpected value for SINGULAR; for xpath " + xpath, (facts & SINGULAR) != 0, 
                    extractor.queries.get(0).isFact(SINGULAR));
            assertEquals ("facts don't match", facts, extractor.queries.get(0).getFacts());      
            if (type != null) {
                if ( ! (type == ValueType.DOCUMENT || type == ValueType.BOOLEAN)) {
                    // the other types don't get passed to lux:search as facts
                    type = ValueType.VALUE;
                }
                assertSame (type, extractor.queries.get(0).getResultType());
            }
        }
    }

    /**
     * each test case uses one or more enums to retrieve the expected generated query
     * @param q query enum identifying the test case
     * @return the query expected for this case
     */
    public String getQueryString(Q q) {
        switch (q) {
        case ATTR: return "lux_att_name:\"attr\"";
        case SCENE: return "lux_elt_name:\"SCENE\"";
        case ACT_SCENE: 
        case ACT_SCENE1:
        case ACT_SCENE2:
        case ACT_SCENE3:
        case ACT_SCENE_CONTENT:
        case ACT_SCENE_CONTENT1:
            return "+lux_elt_name:\"SCENE\" +lux_elt_name:\"ACT\"";
        case SCENE_ACT: return "+lux_elt_name:\"ACT\" +lux_elt_name:\"SCENE\"";
        case ACT_OR_SCENE: return "lux_elt_name:\"SCENE\" lux_elt_name:\"ACT\"";
        case ACT_AND_SCENE: return "+lux_elt_name:\"SCENE\" +lux_elt_name:\"ACT\"";
        case ACT_SCENE_SPEECH: return
            "(+lux_elt_name:\"TITLE\" +lux_elt_name:\"SPEECH\")" +
            " ((+lux_elt_name:\"TITLE\" +lux_elt_name:\"SCENE\")" +
            " (+lux_elt_name:\"TITLE\" +lux_elt_name:\"ACT\"))";
        case LINE:
            return "lux:elt_name:\"LINE\"";
        case ACT:
        case ACT1:
        case ACT2:
        case ACT_CONTENT:
        case ACT_CONTENT1:
            return "lux_elt_name:\"ACT\"";
        case ACT_ID_123:
        case ACT_ID: 
            return "+lux_att_name:\"id\" +lux_elt_name:\"ACT\"";
        case ACT_SCENE_ID_123:
            return "+(+lux_att_name:\"id\" +lux_elt_name:\"SCENE\") +lux_elt_name:\"ACT\"";
        case PLAY_ACT_OR_PERSONAE_TITLE: return "+lux_elt_name:\"TITLE\" +(+(lux_elt_name:\"PERSONAE\" lux_elt_name:\"ACT\") +lux_elt_name:\"PLAY\")";
        case MATCH_ALL: return "*:*";
        case AND: return "lux_elt_name:\"AND\"";
        case LUX_FOO: return "lux_elt_name:\"foo{" + FunCall.LUX_NAMESPACE + "}\"";
        default: throw new UnsupportedOperationException("unregistered query enum: " + q);
        }
    }

    /**
     * each test case uses one or more enums to retrieve the expected generated query
     * @param q query enum identifying the test case
     * @return the query expected for this case
     */
    public String getQueryXml(Q q) {
        switch (q) {
        case ATTR: return "<TermQuery fieldName=\"lux_att_name\">attr</TermQuery>";
        case SCENE: return "<TermQuery fieldName=\"lux_elt_name\">SCENE</TermQuery>";
        case ACT_SCENE: 
        case ACT_SCENE1:
        case ACT_SCENE2:
        case ACT_SCENE3:
        case ACT_SCENE_CONTENT:
        case ACT_SCENE_CONTENT1:
        case ACT_AND_SCENE: 
            return "<BooleanQuery><Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">ACT</TermQuery></Clause>" + 
                "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">SCENE</TermQuery></Clause></BooleanQuery>";
        case SCENE_ACT: 
            return "<BooleanQuery><Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">SCENE</TermQuery></Clause>" + 
                "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">ACT</TermQuery></Clause></BooleanQuery>";
        case ACT_OR_SCENE: 
            return "<BooleanQuery><Clause occurs=\"should\"><TermQuery fieldName=\"lux_elt_name\">ACT</TermQuery></Clause>" + 
                "<Clause occurs=\"should\"><TermQuery fieldName=\"lux_elt_name\">SCENE</TermQuery></Clause></BooleanQuery>";
        case ACT_SCENE_SPEECH:
            return 
                "<BooleanQuery><Clause occurs=\"should\">" +
                  "<BooleanQuery>" +
                    "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">ACT</TermQuery></Clause>" + 
                    "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">TITLE</TermQuery></Clause>" +
                   "</BooleanQuery>" +
                "</Clause><Clause occurs=\"should\">" +
                    "<BooleanQuery>" +
                      "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">SCENE</TermQuery></Clause>" + 
                      "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">TITLE</TermQuery></Clause>" +
                    "</BooleanQuery>" +
                  "</Clause><Clause occurs=\"should\">" +
                  "<BooleanQuery>" +
                    "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">SPEECH</TermQuery></Clause>" + 
                    "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">TITLE</TermQuery></Clause>" +
                  "</BooleanQuery>" +
                "</Clause></BooleanQuery>";

        case LINE:
            return "<TermQuery fieldName=\"lux_elt_name\">LINE</TermQuery>";
        case ACT:
        case ACT1:
        case ACT2:
        case ACT_CONTENT:
        case ACT_CONTENT1:
            return "<TermQuery fieldName=\"lux_elt_name\">ACT</TermQuery>";
        case ACT_ID_123:
        case ACT_ID: 
            return "<BooleanQuery>" +
                    "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">ACT</TermQuery></Clause>" +
            		"<Clause occurs=\"must\"><TermQuery fieldName=\"lux_att_name\">id</TermQuery></Clause>" + 
            		"</BooleanQuery>";
        case ACT_SCENE_ID_123:
            return 
                "<BooleanQuery>" +
                "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">ACT</TermQuery></Clause>" +
                "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">SCENE</TermQuery></Clause>" +
                "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_att_name\">id</TermQuery></Clause>" + 
                "</BooleanQuery>";
        case PLAY_ACT_OR_PERSONAE_TITLE: 
            return 
                "<BooleanQuery>" +
                "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">PLAY</TermQuery></Clause>" + 
                "<Clause occurs=\"must\"><BooleanQuery>" +
                    "<Clause occurs=\"should\"><TermQuery fieldName=\"lux_elt_name\">ACT</TermQuery></Clause>" + 
                    "<Clause occurs=\"should\"><TermQuery fieldName=\"lux_elt_name\">PERSONAE</TermQuery></Clause>" + 
                  "</BooleanQuery></Clause>" +
                  "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">TITLE</TermQuery></Clause>" + 
                "</BooleanQuery>";
        case TITLE:
            return "<TermQuery fieldName=\"lux_elt_name\">TITLE</TermQuery>";
        case MATCH_ALL: return "<MatchAllDocsQuery />";
        case AND: return "<TermQuery fieldName=\"lux_elt_name\">AND</TermQuery>";
        case LUX_FOO: return "<TermQuery fieldName=\"lux_elt_name\">foo&#x7B;http://luxdb.net&#x7D;</TermQuery>";
        default: throw new UnsupportedOperationException("unregistered query enum: " + q);
        }
    }

    public XmlIndexer getIndexer() {
        return new XmlIndexer(IndexConfiguration.INDEX_QNAMES);
    }

    static class MockQuery extends XPathQuery {
        private final String queryString;

        MockQuery (String queryString, long facts) {
            super (null, facts, typeFromFacts (facts));
            this.queryString = queryString;
        }

        @Override
        public String toString() {
            return queryString;
        }
        
        @Override
        public ValueType getResultType () {
            if (isFact(DOCUMENT_RESULTS)) {
                return ValueType.DOCUMENT;
            }
            if (isFact(BOOLEAN_FALSE) || isFact(BOOLEAN_TRUE)) {
                return ValueType.BOOLEAN;
            }
            return ValueType.VALUE;
        }
    }

    static class SearchExtractor extends ExpressionVisitorBase {
        ArrayList<XPathQuery> queries = new ArrayList<XPathQuery>();
        
        @Override
        public FunCall visit (FunCall funcall) {
            if (funcall.getName().equals (FunCall.LUX_SEARCH)
                    || funcall.getName().equals (FunCall.LUX_COUNT) 
                    || funcall.getName().equals (FunCall.LUX_EXISTS)) 
            {
                AbstractExpression queryArg = funcall.getSubs()[0];
                String q = (queryArg instanceof LiteralExpression) ? ((LiteralExpression)queryArg).getValue().toString()
                        : queryArg.toString();
                long facts=0;
                if (funcall.getSubs().length > 1) {
                    facts = (Long) ((LiteralExpression)funcall.getSubs()[1]).getValue();
                }
                queries.add( new MockQuery (q, facts));
            }
            return funcall;
        }
        
    }
    
    protected boolean hasPathIndexes() {
        return false;
    }
    
    static class SortExtractor extends ExpressionVisitorBase {
        ArrayList<String> sorts = new ArrayList<String>();
        
        @Override
        public FunCall visit (FunCall funcall) {
            if (funcall.getName().equals (FunCall.LUX_SEARCH)) {
                if (funcall.getSubs().length >= 3) {
                    AbstractExpression sortArg = funcall.getSubs()[2];
                    String s = ((LiteralExpression)sortArg).getValue().toString();
                    sorts.add(s);
                }
            }
            return funcall;
        }
        
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
