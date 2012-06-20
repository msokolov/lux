package lux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;

import lux.api.ValueType;
import lux.index.XmlIndexer;
import lux.saxon.Saxon;
import lux.saxon.Saxon.Dialect;
import lux.saxon.SaxonExpr;
import lux.xpath.AbstractExpression;
import lux.xpath.FunCall;
import lux.xpath.LiteralExpression;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests the parsing of XPath expressions and the generation
 * of a supporting Lucene query using node name indexes, and using path indexes.
 */
public abstract class BasicQueryTest {

    public abstract XmlIndexer getIndexer ();

    /**
     * each test case uses one or more enums to retrieve the expected generated query
     * @param q query enum identifying the test case
     * @return the query expected for this case
     */
    public abstract String getQueryString (Q q);

    public enum Q {
        ATTR, SCENE, SCENE_ACT, 
            ACT, ACT1, ACT2, ACT_CONTENT, ACT_CONTENT1, ACT_SCENE, ACT_SCENE1, ACT_SCENE_CONTENT, ACT_SCENE_CONTENT1, ACT_SCENE_SPEECH, ACT_OR_SCENE, 
            ACT_ID, ACT_ID_123, ACT_SCENE_ID_123,
            MATCH_ALL, ACT_SCENE2, ACT_AND_SCENE, ACT_SCENE3, AND, PLAY_ACT_OR_PERSONAE_TITLE, 
            LUX_FOO, 
    };
    
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
        assertQuery ("/self::node()", XPathQuery.MINIMAL|XPathQuery.DOCUMENT_RESULTS, ValueType.DOCUMENT, Q.MATCH_ALL);
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
    public void testConvertRootedPathToPredicate() {
        assertQuery ("//ACT/SCENE/root()", "lux:search(\"" + 
                     getQueryString(Q.ACT_SCENE).replace("\"", "\"\"") + "\",24)" +
        		"[(descendant::ACT/child::SCENE)/root(.)]", 
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
        assertQuery ("/PLAY/(ACT|PERSONAE)/TITLE", 0, ValueType.ELEMENT, Q.PLAY_ACT_OR_PERSONAE_TITLE);
    }

    @Test @Ignore public void testElementValueNoPath () throws Exception {
        // depends on context expression when there is none
        assertQuery ("ACT[.='content']", 0, ValueType.ELEMENT);
    }
    
    @Test public void testElementValue () throws Exception {
        assertQuery ("/ACT[.='content']", 0, ValueType.ELEMENT, Q.ACT_CONTENT1);

        assertQuery ("/ACT[SCENE='content']", 0, ValueType.ELEMENT, Q.ACT_SCENE_CONTENT1);

        assertQuery ("//ACT[.='content']", 0, ValueType.ELEMENT, Q.ACT_CONTENT);

        assertQuery ("//ACT[SCENE='content']", 0, ValueType.ELEMENT, Q.ACT_SCENE_CONTENT);
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
        assertQuery ("count(//ACT/SCENE/ancestor::document-node())", 0, ValueType.ATOMIC, Q.ACT_SCENE);
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
        assertQuery ("collection()//SCENE", "lux:search(\"" +
                getQueryString(Q.SCENE).replace("\"", "\"\"")
                + "\",2)/descendant::SCENE", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.SCENE);
    }
    
    public void assertQuery (String xpath, int facts, Q ... queries) {
        assertQuery (xpath, facts, null, queries);
    }

    public void assertQuery (String xpath, int facts, ValueType type, Q ... queries) {
        assertQuery (xpath, null, facts, type, queries);
    }
    
    /**
     * asserts that the given xpath generates the lucene query string, and 
     * that the asserted facts are passed to lux:search
     * 
     * @param xpath the expression to be tested
     * @param facts facts expected for the first query
     * @param type return type expected for the first query
     * @param queries the expected lucene query strings
     */

    public void assertQuery (String xpath, String optimized, int facts, ValueType type, Q ... queries) {
        Saxon saxon = new Saxon(null, getIndexer(), Dialect.XPATH_2);
        saxon.declareNamespace("lux", "lux");
        saxon.declareNamespace("ns", "http://namespace.org/#ns");
        assertQuery(xpath, optimized, facts, type, saxon, queries);
    }

    private void assertQuery(String xpath, String optimized, int facts, ValueType type, Saxon saxon,
            Q ... queries) {

        SaxonExpr expr = saxon.compile(xpath);
        AbstractExpression ex = expr.getXPath();
        if (optimized != null) {
            assertEquals (optimized, ex.toString());
        }
        SearchExtractor extractor = new SearchExtractor();
        ex.accept(extractor);
        assertEquals ("wrong number of queries for " + xpath, queries.length, extractor.queries.size());
        for (int i = 0; i < queries.length; i++) {
            assertEquals (getQueryString(queries[i]), extractor.queries.get(i).toString());
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

    static class MockQuery extends XPathQuery {
        private String queryString;

        MockQuery (String queryString, long facts) {
            super (null, null, facts, XPathQuery.typeFromFacts (facts));
            this.facts = facts;
            this.queryString = queryString;
        }

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
        
        public FunCall visit (FunCall funcall) {
            if (funcall.getName().equals (FunCall.LUX_SEARCH)
                    || funcall.getName().equals (FunCall.LUX_COUNT) 
                    || funcall.getName().equals (FunCall.LUX_EXISTS)) {
                String q = ((LiteralExpression)funcall.getSubs()[0]).getValue().toString();
                long facts = (Long) ((LiteralExpression)funcall.getSubs()[1]).getValue();
                queries.add( new MockQuery (q, facts));
            }
            return funcall;
        }
        
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
