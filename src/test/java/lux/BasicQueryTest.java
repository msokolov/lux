package lux;

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

/**
 * Tests the parsing of XPath expressions and the generation
 * of a supporting Lucene query using node name indexes, and using path indexes.
 */
public class BasicQueryTest {
    
    public enum Q {
        ATTR, SCENE, SCENE_ACT, 
            ACT, ACT1, ACT2, ACT_CONTENT, ACT_CONTENT1, ACT_SCENE, ACT_SCENE1, ACT_SCENE_CONTENT, ACT_SCENE_CONTENT1, ACT_SCENE_SPEECH, ACT_OR_SCENE, 
            ACT_ID, ACT_ID_123, ACT_SCENE_ID_123,
            MATCH_ALL, ACT_SCENE2, ACT_AND_SCENE, ACT_SCENE3, AND, PLAY_ACT_OR_PERSONAE_TITLE, 
            LUX_FOO, LINE, TITLE, 
    };
    
    protected Compiler compiler;
    protected Evaluator eval;
    
    @Before public void setup () {
        compiler = new Compiler(getIndexer().getConfiguration());
        eval = new Evaluator(compiler, null, null);
    }
    
    @Test public void testNoQuery () throws Exception {

        try {
            assertQuery ("", XPathQuery.MINIMAL, ValueType.DOCUMENT);
            assertFalse ("expected syntax error", true);
        } catch (Exception e) { }

        try {
            assertQuery (null, XPathQuery.MINIMAL, ValueType.DOCUMENT);
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
        assertQuery ("/*", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.MATCH_ALL);
        assertQuery ("/node()", XPathQuery.MINIMAL, ValueType.NODE, Q.MATCH_ALL);
        //assertQuery ("self::node()", Q.MATCH_ALL, XPathQuery.MINIMAL);
    }
    
    @Test public void testSlash() throws Exception {
        assertQuery ("/", XPathQuery.MINIMAL|XPathQuery.DOCUMENT_RESULTS, ValueType.DOCUMENT, Q.MATCH_ALL);
    }
    
    @Test public void testElementPredicate() throws Exception {
        assertQuery ("(/)[.//ACT]", XPathQuery.MINIMAL|XPathQuery.DOCUMENT_RESULTS, ValueType.DOCUMENT, Q.ACT); 
    }

    @Test public void testElementPaths () throws Exception {
       
        assertQuery ("//ACT", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.ACT);

        assertQuery ("/*/ACT", 0, ValueType.ELEMENT, Q.ACT2);
        
        assertQuery ("/ACT//*", 0, ValueType.ELEMENT, Q.ACT1);

        assertQuery ("/ACT", 0, ValueType.ELEMENT, Q.ACT1);
        
        // this should be ValueType.TEXT shouldn't it??
        assertQuery ("/ACT/text()", 0, ValueType.ELEMENT, Q.ACT1);

        assertQuery ("//*/@attr", XPathQuery.MINIMAL, ValueType.ATTRIBUTE, Q.ATTR);

        assertQuery ("//node()/@attr", XPathQuery.MINIMAL, ValueType.ATTRIBUTE, Q.ATTR);
    }

    @Test
    public void testConvertRootedPathToPredicate() throws Exception {
        assertQuery ("//ACT/SCENE/root()", "lux:search(" + 
                     getQueryXml(Q.ACT_SCENE) + ",24)" +
        		"[(descendant::element(ACT)/child::element(SCENE))/root(.)]", 
        		XPathQuery.DOCUMENT_RESULTS, ValueType.DOCUMENT, Q.ACT_SCENE);
    }    
    
    @Test public void testAttributePredicates () throws Exception {
        assertQuery ("//*[@attr]", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.ATTR);

        assertQuery ("(/)[.//*/@attr]", XPathQuery.MINIMAL|XPathQuery.DOCUMENT_RESULTS, ValueType.DOCUMENT, Q.ATTR);        
    }

    @Test public void testElementAttributePaths () throws Exception {
        
        assertQuery ("//ACT/@id", XPathQuery.MINIMAL, ValueType.ATTRIBUTE, Q.ACT_ID);

        assertQuery ("//ACT/@*", XPathQuery.MINIMAL, ValueType.ATTRIBUTE, Q.ACT);
    }

    @Test public void testTwoElementPaths () throws Exception {
        assertQuery ("//*/ACT/SCENE", 0, ValueType.ELEMENT, Q.ACT_SCENE);
        assertQuery ("/ACT/SCENE", 0, ValueType.ELEMENT, Q.ACT_SCENE1);
        assertQuery ("/ACT//SCENE", 0, ValueType.ELEMENT, Q.ACT_SCENE2);
    }
    
    @Test public void testTwoElementPredicates () throws Exception {
        assertQuery ("(/)[.//ACT][.//SCENE]", XPathQuery.MINIMAL|XPathQuery.DOCUMENT_RESULTS, ValueType.DOCUMENT, Q.ACT_SCENE3);
    }
    
    @Test public void testUnion () throws Exception {
        // can't execute without context item
        // assertQuery ("ACT|SCENE", 0, ValueType.ELEMENT);

        assertQuery ("//ACT|//SCENE", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.ACT_OR_SCENE);
    }

    @Test public void testPositionalPredicate () throws Exception {
        assertQuery ("//ACT/SCENE[1]", 0, ValueType.ELEMENT, Q.ACT_SCENE);
        assertQuery ("/descendant-or-self::SCENE[1]", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.SCENE);
        assertQuery ("//SCENE[1]", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.SCENE);        
        assertQuery ("//SCENE[last()]", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.SCENE);

        // not minimal, since there may not be a second SCENE element
        assertQuery ("//SCENE[2]", 0, ValueType.ELEMENT, Q.SCENE);
        
        assertQuery ("(//ACT)[1]", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.ACT); 
    }
    
    @Test public void testSubsequence () throws Exception {
        assertQuery ("subsequence (//ACT, 1, 1)", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.ACT);
        // save this until we have implemented some kind of pagination in XPathQuery
        // lux:search and XPathCollector
        // assertQuery ("subsequence (//ACT, 2)", 0, Q.ACT);
        assertQuery ("subsequence (//ACT, 1, 10)", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.ACT);
        //assertQuery ("subsequence (//ACT, 10, 10)", 0, Q.ACT);
    }

    @Test public void testMultiElementPaths () throws Exception {        
        assertQuery ("//ACT/TITLE | //SCENE/TITLE| //SPEECH/TITLE",
                     0,
                     ValueType.ELEMENT, Q.ACT_SCENE_SPEECH);
        // This was three separate queries, whose results would then have to be merged together,
        // but our Optimizer declares all these expressions as ordered, enabling Saxon to merge them 
        // together into a single query
        // FIXME: currently failing since the Optimizer is not installed (and will fail w/Saxon PE, etc)
        assertQuery ("/PLAY/(ACT|PERSONAE)/TITLE", 0, ValueType.ELEMENT, Q.PLAY_ACT_OR_PERSONAE_TITLE);
    }
    
    @Test public void testManySmallDocs () throws Exception {
        assertQuery ("/LINE", 0, ValueType.ELEMENT, Q.LINE);        
    }

    @Test @Ignore public void testElementValueNoPath () throws Exception {
        // depends on context expression when there is none
        assertQuery ("ACT[.='content']", 0, ValueType.ELEMENT);
    }
    
    @Test public void testElementValue () throws Exception {
        assertQuery ("/ACT[.='content']", 0, ValueType.ELEMENT, Q.ACT_CONTENT1);

        assertQuery ("/ACT[SCENE='content']", 0, ValueType.ELEMENT, Q.ACT_SCENE_CONTENT1);
        assertQuery ("/ACT['content'=SCENE]", 0, ValueType.ELEMENT, Q.ACT_SCENE_CONTENT1);
        assertQuery ("/ACT/SCENE[.='content']", 0, ValueType.ELEMENT, Q.ACT_SCENE_CONTENT1);

        assertQuery ("//ACT[.='content']", 0, ValueType.ELEMENT, Q.ACT_CONTENT);

        assertQuery ("//ACT[SCENE='content']", 0, ValueType.ELEMENT, Q.ACT_SCENE_CONTENT);
    }
    
    @Test public void testElementValueSelf() throws Exception {
        //assertQuery ("/ACT/SCENE[self::node()='content']", 0, ValueType.ELEMENT, Q.ACT_SCENE_CONTENT1);        
        //assertQuery ("/*[self::ACT='content']", 0, ValueType.ELEMENT, Q.ACT_CONTENT1);
        assertQuery ("/*[self::ACT/SCENE='content']", 0, ValueType.ELEMENT, Q.ACT_SCENE_CONTENT1);
        assertQuery ("/*[self::ACT/SCENE/self::*='content']", 0, ValueType.ELEMENT, Q.ACT_SCENE_CONTENT1);
    }
    
    @Test public void testAttributeValue () throws Exception {
        assertQuery ("/ACT[@id=123]", 0, ValueType.ELEMENT, Q.ACT_ID_123);

        assertQuery ("/ACT[SCENE/@id=123]", 0, ValueType.ELEMENT, Q.ACT_SCENE_ID_123);
    }

    @Test public void testAncestorOrSelf () throws Exception {
        assertQuery ("/ancestor-or-self::node()", XPathQuery.MINIMAL|XPathQuery.DOCUMENT_RESULTS, ValueType.DOCUMENT, Q.MATCH_ALL);
    }
    
    @Test public void testSelf () throws Exception {
        assertQuery ("/self::node()", XPathQuery.MINIMAL|XPathQuery.DOCUMENT_RESULTS, ValueType.DOCUMENT, Q.MATCH_ALL);
    }
    
    @Test public void testAtomicResult () throws Exception {
        assertQuery ("number((/ACT/SCENE)[1])", 0, ValueType.ATOMIC, Q.ACT_SCENE1);
        assertQuery ("number((/descendant-or-self::ACT)[1])", XPathQuery.MINIMAL, ValueType.ATOMIC, Q.ACT);
    }
    
    @Test public void testCounting () throws Exception {
        assertQuery ("count(/)", XPathQuery.COUNTING | XPathQuery.MINIMAL, ValueType.ATOMIC, Q.MATCH_ALL);
        assertQuery ("count(//ACT)", XPathQuery.MINIMAL, ValueType.ATOMIC, Q.ACT);
        assertQuery ("count(//ACT/root())", XPathQuery.COUNTING | XPathQuery.MINIMAL, ValueType.ATOMIC, Q.ACT);
        assertQuery ("count(//ACT/ancestor::document-node())", XPathQuery.COUNTING | XPathQuery.MINIMAL, ValueType.ATOMIC, Q.ACT);
        // FIXME: the optimizer should mark this as minimal/counting too now that we have path queries
        assertQuery ("count(//ACT/SCENE/ancestor::document-node())", XPathQuery.DOCUMENT_RESULTS, ValueType.DOCUMENT, Q.ACT_SCENE);
    }
    
    @Test public void testExistence() throws Exception {
        assertQuery ("exists(/)", XPathQuery.MINIMAL|XPathQuery.BOOLEAN_TRUE, ValueType.BOOLEAN, Q.MATCH_ALL);
        assertQuery ("exists(//ACT)", XPathQuery.MINIMAL|XPathQuery.BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT);
        assertQuery ("exists(//ACT/root())", XPathQuery.MINIMAL|XPathQuery.BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT);
        assertQuery ("exists(//ACT) and exists(//SCENE)", XPathQuery.MINIMAL|XPathQuery.BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT, Q.SCENE);
        assertQuery ("exists(//ACT/root()//SCENE)", XPathQuery.MINIMAL|XPathQuery.BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT_AND_SCENE);
        // TODO: merge queries such as this into one:
        // assertQuery ("exists(//ACT/root() intersect //SCENE/root())", XPathQuery.MINIMAL, ValueType.BOOLEAN, Q.ACT_SCENE);
        assertQuery ("exists((/)[.//ACT and .//SCENE])", XPathQuery.MINIMAL|XPathQuery.BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT_AND_SCENE);
        assertQuery ("//ACT[exists(SCENE)]", 0, ValueType.NODE, Q.ACT_SCENE);
    }
    
    @Test public void testNonexistence() throws Exception {
        assertQuery ("empty(/)", XPathQuery.MINIMAL|XPathQuery.BOOLEAN_FALSE, ValueType.BOOLEAN, Q.MATCH_ALL);
        assertQuery ("empty(//ACT)", XPathQuery.MINIMAL|XPathQuery.BOOLEAN_FALSE, ValueType.BOOLEAN, Q.ACT);
        assertQuery ("empty(//ACT/root())", XPathQuery.MINIMAL|XPathQuery.BOOLEAN_FALSE, ValueType.BOOLEAN, Q.ACT);
        assertQuery ("empty(//ACT) and empty(//SCENE)", XPathQuery.MINIMAL|XPathQuery.BOOLEAN_FALSE, ValueType.BOOLEAN, Q.ACT, Q.SCENE);
        assertQuery ("empty(//ACT/root()//SCENE)", XPathQuery.MINIMAL|XPathQuery.BOOLEAN_FALSE, ValueType.BOOLEAN, Q.ACT_AND_SCENE);
        assertQuery ("empty((/)[.//ACT and .//SCENE])", XPathQuery.MINIMAL|XPathQuery.BOOLEAN_FALSE, ValueType.BOOLEAN, Q.ACT_AND_SCENE);

        assertQuery ("//ACT[empty(SCENE)]", 0, ValueType.NODE, Q.ACT);
    }
    
    @Test public void testNot() throws Exception {
        assertQuery ("not(/)", XPathQuery.MINIMAL|XPathQuery.BOOLEAN_TRUE, ValueType.BOOLEAN, Q.MATCH_ALL);
        assertQuery ("not(//ACT)", XPathQuery.MINIMAL|XPathQuery.BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT);
        assertQuery ("not(//ACT/root())", XPathQuery.MINIMAL|XPathQuery.BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT);
        assertQuery ("not(//ACT) and empty(//SCENE)", XPathQuery.MINIMAL|XPathQuery.BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT, Q.SCENE);
        assertQuery ("not(//ACT/root()//SCENE)", XPathQuery.MINIMAL|XPathQuery.BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT_AND_SCENE);
        assertQuery ("not((/)[.//ACT and .//SCENE])", XPathQuery.MINIMAL|XPathQuery.BOOLEAN_TRUE, ValueType.BOOLEAN, Q.ACT_AND_SCENE);
    }
    @Test public void testPredicateNegation () throws Exception {
        assertQuery ("//ACT[not(SCENE)]", 0, ValueType.ELEMENT, Q.ACT);
        assertQuery ("//ACT[count(SCENE) = 0]", 0, ValueType.ELEMENT, Q.ACT);
    }
    
    @Test public void testPredicateCombine () throws Exception {
        // This should depend on having both ACT and SCENE
        assertQuery ("//ACT[.//SCENE]", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.ACT_SCENE3);
        assertQuery ("//ACT[exists(.//SCENE)]", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.ACT_SCENE3);        
        // TODO: optimize not() expressions involving exists() and empty()
        // This should depend on having a SCENE!
        assertQuery ("//ACT[not(empty(.//SCENE))]", 0, ValueType.ELEMENT, Q.ACT); 
    }
    
    @Test public void testReservedWords () throws Exception {
        // internally, we use certain words to denote search operations.  Make sure these are not
        // confused with query terms
        assertQuery("//AND", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.AND);        
    }

    @Test public void testNamespaceAware () throws Exception {
        assertQuery("//lux:foo", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.LUX_FOO);
    }
    
    @Test public void testCollection () throws Exception {
        // fn:collection() is implicit
        assertQuery ("collection()//SCENE", "lux:search(" +
                getQueryXml(Q.SCENE)
                + ",2)/descendant::element(SCENE)", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.SCENE);
    }
    
    @Test public void testOrderBy () throws Exception {
        String query = "for $doc in //ACT order by $doc/lux:field-values('sortkey') return $doc";
        assertQuery (query, XPathQuery.MINIMAL, ValueType.ELEMENT, Q.ACT);
        assertSortKeys (query, "sortkey");
    }
    
    @Test public void testOrderByContextArgument () throws Exception {
        String query = "for $doc in //ACT order by lux:field-values('sortkey', $doc) return $doc";
        assertQuery (query, XPathQuery.MINIMAL, ValueType.ELEMENT, Q.ACT);
        assertSortKeys (query, "sortkey");
    }

    @Test public void testOrderBySearchFunCall () throws Exception {
        String query = "for $doc in lux:search('foo') order by lux:field-values('sortkey', $doc) return $doc";
        assertQuery (query, null, XPathQuery.MINIMAL, ValueType.VALUE, "foo");
        assertSortKeys (query, "sortkey");
    }
    @Test 
    public void testOrderBy2Keys () throws Exception {
        // two indexed sortkeys
        String query = "for $doc in //ACT order by $doc/lux:field-values('sortkey'), $doc/lux:field-values('sk2') return $doc";
        assertQuery (query, XPathQuery.MINIMAL, ValueType.ELEMENT, Q.ACT);
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
        assertQuery (query, 0, Q.MATCH_ALL);

        query = "(for $doc in collection() return data($doc//TITLE))[2]";
        assertQuery (query, XPathQuery.MINIMAL, Q.TITLE);

    }
    
    public void assertQuery (String xpath, int facts, Q ... queries) throws Exception {
        assertQuery (xpath, facts, null, queries);
    }

    public void assertQuery (String xpath, int facts, ValueType type, Q ... queries) throws Exception{
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

    public void assertQuery (String xpath, String expectedOptimized, int facts, ValueType type, Q ... queries) throws Exception {
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
            boolean isMinimal = (facts & XPathQuery.MINIMAL) != 0;
            assertEquals ("isMinimal was not " + isMinimal + " for xpath " + xpath,
                    isMinimal, extractor.queries.get(0).isFact(XPathQuery.MINIMAL));
            assertEquals ("query counting for xpath " + xpath, (facts & XPathQuery.COUNTING) != 0, 
                    extractor.queries.get(0).isFact(XPathQuery.COUNTING));
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
            return "<BooleanQuery><Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">SCENE</TermQuery></Clause>" + 
                "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">ACT</TermQuery></Clause></BooleanQuery>";
        case SCENE_ACT: 
            return "<BooleanQuery><Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">ACT</TermQuery></Clause>" + 
                "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">SCENE</TermQuery></Clause></BooleanQuery>";
        case ACT_OR_SCENE: 
            return "<BooleanQuery><Clause occurs=\"should\"><TermQuery fieldName=\"lux_elt_name\">SCENE</TermQuery></Clause>" + 
                "<Clause occurs=\"should\"><TermQuery fieldName=\"lux_elt_name\">ACT</TermQuery></Clause></BooleanQuery>";
        case ACT_SCENE_SPEECH:
            return 
                "<BooleanQuery><Clause occurs=\"should\">" +
                  "<BooleanQuery>" +
                    "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">TITLE</TermQuery></Clause>" + 
                    "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">SPEECH</TermQuery></Clause>" +
                   "</BooleanQuery>" +
                "</Clause><Clause occurs=\"should\">" +
                  "<BooleanQuery><Clause occurs=\"should\">" +
                    "<BooleanQuery>" +
                      "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">TITLE</TermQuery></Clause>" + 
                      "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">SCENE</TermQuery></Clause>" +
                    "</BooleanQuery>" +
                  "</Clause><Clause occurs=\"should\">" +
                  "<BooleanQuery>" +
                    "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">TITLE</TermQuery></Clause>" + 
                    "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">ACT</TermQuery></Clause>" +
                  "</BooleanQuery>" +
                  "</Clause></BooleanQuery>" +
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
            return "<BooleanQuery><Clause occurs=\"must\"><TermQuery fieldName=\"lux_att_name\">id</TermQuery></Clause>" + 
                "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">ACT</TermQuery></Clause></BooleanQuery>";
        case ACT_SCENE_ID_123:
            return 
                "<BooleanQuery><Clause occurs=\"must\">" +
                "<BooleanQuery><Clause occurs=\"must\"><TermQuery fieldName=\"lux_att_name\">id</TermQuery></Clause>" + 
                "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">SCENE</TermQuery></Clause></BooleanQuery>" +
                "</Clause>" +
                "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">ACT</TermQuery></Clause>" + 
                "</BooleanQuery>";
        case PLAY_ACT_OR_PERSONAE_TITLE: 
            return 
                "<BooleanQuery>" +
                "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">TITLE</TermQuery></Clause>" + 
                "<Clause occurs=\"must\"><BooleanQuery>" +
                  "<Clause occurs=\"must\"><BooleanQuery>" +
                    "<Clause occurs=\"should\"><TermQuery fieldName=\"lux_elt_name\">PERSONAE</TermQuery></Clause>" + 
                    "<Clause occurs=\"should\"><TermQuery fieldName=\"lux_elt_name\">ACT</TermQuery></Clause>" + 
                  "</BooleanQuery></Clause>" +
                  "<Clause occurs=\"must\"><TermQuery fieldName=\"lux_elt_name\">PLAY</TermQuery></Clause>" + 
                "</BooleanQuery></Clause>" +
                "</BooleanQuery>";
        case TITLE:
            return "<TermQuery fieldName=\"lux_elt_name\">TITLE</TermQuery>";
        case MATCH_ALL: return "<MatchAllDocsQuery />";
        case AND: return "<TermQuery fieldName=\"lux_elt_name\">AND</TermQuery>";
        case LUX_FOO: return "<TermQuery fieldName=\"lux_elt_name\">foo&#x7B;http://luxproject.net&#x7D;</TermQuery>";
        default: throw new UnsupportedOperationException("unregistered query enum: " + q);
        }
    }

    public XmlIndexer getIndexer() {
        return new XmlIndexer(IndexConfiguration.INDEX_QNAMES);
    }

    static class MockQuery extends XPathQuery {
        private final String queryString;

        MockQuery (String queryString, long facts) {
            super (null, facts, XPathQuery.typeFromFacts (facts));
            this.queryString = queryString;
        }

        @Override
        public String toString() {
            return queryString;
        }
        
        @Override
        public ValueType getResultType () {
            if (isFact(XPathQuery.DOCUMENT_RESULTS)) {
                return ValueType.DOCUMENT;
            }
            if (isFact(XPathQuery.BOOLEAN_FALSE) || isFact(XPathQuery.BOOLEAN_TRUE)) {
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
