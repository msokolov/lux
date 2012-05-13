package lux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.HashMap;

import lux.api.ValueType;
import lux.index.XmlIndexer;
import lux.saxon.Saxon;
import lux.saxon.SaxonContext;
import lux.saxon.SaxonExpr;
import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitorBase;
import lux.xpath.FunCall;
import lux.xpath.LiteralExpression;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests the parsing of XPath expressions and the generation
 * of a supporting Lucene query using node name indexes, and using path indexes.
 * 
 * TODO: add some tests with empty steps like self::* and self::node() axis 
 */
public abstract class BasicQueryTest {
    
    protected HashMap <Q, String> queryStrings;

    // subclasses supply expected query strings corresponding to each test case
    // in the Q enum
    public abstract void populateQueryStrings ();
    
    public abstract XmlIndexer getIndexer ();
    
    public enum Q {
        ATTR, SCENE, SCENE_ACT, 
            ACT, ACT1, ACT2, ACT_SCENE, ACT_SCENE1, ACT_SCENE_SPEECH, ACT_OR_SCENE, 
            ACT_ID, MATCH_ALL, ACT_SCENE2, ACT_AND_SCENE, ACT_SCENE3, 
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
        // assertQuery ("ACT/text()", Q.ACT, false public void testAttributePaths ( {
        // FIXME: compute minimality properly for attributes
        
        assertQuery ("//*/@attr", XPathQuery.MINIMAL, ValueType.ATTRIBUTE, Q.ATTR);
        
        assertQuery ("//node()/@attr", XPathQuery.MINIMAL, ValueType.ATTRIBUTE, Q.ATTR);
    }    

    @Test
    public void testConvertRootedPathToPredicate() {
        assertQuery ("//ACT/SCENE/root()", "lux:search(\"" + getQueryString(Q.ACT_SCENE) + "\",24)" +
        		"[exists(descendant::element(ACT)/child::element(SCENE)/root(.))]", 
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
                     /*
                     "+lux_elt_name:ACT +lux_elt_name:title",
                     "+lux_elt_name:SCENE +lux_elt_name:title",
                     "+lux_elt_name:SPEECH +lux_elt_name:title"); 
                     */
    }

    @Test @Ignore public void testElementValueNoPath () throws Exception {
        // depends on context expression when there is none
        assertQuery ("ACT[.='content']", 0, ValueType.ELEMENT);
    }
    
    @Test public void testElementValue () throws Exception {
        assertQuery ("/ACT[.='content']", 0, ValueType.ELEMENT, Q.ACT1);

        assertQuery ("/ACT[SCENE='content']", 0, ValueType.ELEMENT, Q.ACT_SCENE1);

        assertQuery ("//ACT[.='content']", 0, ValueType.ELEMENT, Q.ACT);

        assertQuery ("//ACT[SCENE='content']", 0, ValueType.ELEMENT, Q.ACT_SCENE);

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
    // TODO: optimize not() expressions involving exists() and empty()
    
    @Test public void testPredicateNegation () throws Exception {
        assertQuery ("//ACT[not(SCENE)]", 0, ValueType.ELEMENT, Q.ACT);
        assertQuery ("//ACT[count(SCENE) = 0]", 0, ValueType.ELEMENT, Q.ACT);
    }
    
    @Test public void testPredicateCombine () throws Exception {
        // This should depend on having both ACT and SCENE
        assertQuery ("//ACT[.//SCENE]", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.ACT_SCENE3);
        assertQuery ("//ACT[exists(.//SCENE)]", XPathQuery.MINIMAL, ValueType.ELEMENT, Q.ACT_SCENE3);
        // sorry...
        assertQuery ("//ACT[not(empty(.//SCENE))]", 0, ValueType.ELEMENT, Q.ACT); 
    }

    public String getQueryString (Q q) {
        return queryStrings.get(q);
    }
    
    
    @Before 
    public void init () {
        queryStrings = new HashMap<Q, String>();
        populateQueryStrings ();
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
        Saxon saxon = new Saxon();
        
        saxon.setContext(new SaxonContext (null, getIndexer()));
        assertQuery(xpath, optimized, facts, type, saxon, queries);
    }


    private void assertQuery(String xpath, String optimized, int facts, ValueType type, Saxon saxon,
            Q ... queries) {

        SaxonExpr expr = saxon.compile(xpath);
        AbstractExpression ex = saxon.getTranslator().exprFor(expr.getXPathExecutable().getUnderlyingExpression().getInternalExpression());
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
            if (funcall.getQName().equals (FunCall.luxSearchQName)
                    || funcall.getQName().equals (FunCall.luxCountQName) 
                    || funcall.getQName().equals (FunCall.luxExistsQName)) {
                String q = ((LiteralExpression)funcall.getSubs()[0]).getValue().toString();
                long facts = (Long) ((LiteralExpression)funcall.getSubs()[1]).getValue();
                queries.add( new MockQuery (q, facts));
            }
            return funcall;
        }
        
    }
}