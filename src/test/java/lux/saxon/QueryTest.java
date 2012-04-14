package lux.saxon;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;

import lux.api.ValueType;
import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitorBase;
import lux.xpath.FunCall;
import lux.xpath.LiteralExpression;

import org.junit.Test;

/*
 * test XPath 2.0 parsing, query generation and analysis for a variety of expressions
 */
public class QueryTest {

    private static final String Q_FOO = "lux_elt_name_ms:foo";
    private static final String Q_BAR = "lux_elt_name_ms:bar";
    private static final String Q_FOO_BAR = "+lux_elt_name_ms:foo +lux_elt_name_ms:bar";
    private static final String Q_FOO_OR_BAR = "lux_elt_name_ms:foo lux_elt_name_ms:bar";

    @Test public void testRoot() {
        assertQuery ("//foo/root()", Q_FOO, true, ValueType.DOCUMENT);
        
        // These expressions depend on the context item but there is none defined:
        /*
        assertQuery ("foo/root()", Q_FOO, false, ValueType.DOCUMENT);
        
        assertQuery ("document-node()[.//foo]", Q_FOO, true, ValueType.DOCUMENT);
        
        assertQuery ("document-node()[foo]", Q_FOO, false, ValueType.DOCUMENT);

        assertQuery ("node()", "*:*", true, ValueType.NODE);
        
        assertQuery ("node()[.//foo]", Q_FOO, true, ValueType.NODE);
        */
        // In order to achieve the requisite de-duplication, and to be more efficient
        // we need to execute this using a single query not two:
        assertQuery ("//foo/root()|//bar/root()", "lux_elt_name_ms:foo", "lux_elt_name_ms:bar");//, true, ValueType.DOCUMENT);
    }
    
    // FIXME - we need to distinguish count (exact expr) from count(minimal expr)
    @Test public void testCount() {
        assertQuery ("count (/)", "*:*");
        // XPathQuery.COUNTING | XPathQuery.MINIMAL, ValueType.ATOMIC);
        
        assertQuery ("count (//foo)", Q_FOO);//, XPathQuery.COUNTING | XPathQuery.MINIMAL, ValueType.ATOMIC);
        
        assertQuery ("count (/foo/bar)", Q_FOO_BAR);//, XPathQuery.COUNTING, ValueType.ATOMIC);
        
        assertQuery ("count (//foo[3])", Q_FOO); //null, XPathQuery.COUNTING, ValueType.ATOMIC);
        
        //assertQuery ("/descendant::foo[position() = (count(//foo) div 2)]", Q_FOO, Q_FOO);//null, 0, ValueType.ELEMENT);
    }
    
    @Test public void testSumCounts() {
        
        assertQuery ("count (//foo | //bar)", Q_FOO_OR_BAR);//, XPathQuery.COUNTING | XPathQuery.MINIMAL, ValueType.ATOMIC);

        assertQuery ("count (//foo) + count(//bar)", Q_FOO, Q_BAR);//, XPathQuery.COUNTING | XPathQuery.MINIMAL, ValueType.ATOMIC);
    }
    
    @Test public void testNot () throws Exception {
        assertQuery ("not(//foo)", Q_FOO);//, XPathQuery.MINIMAL, ValueType.BOOLEAN);
        assertQuery ("not(//foo | //bar)", Q_FOO_OR_BAR);
        assertQuery ("not(//foo) or not(//bar)", Q_FOO, Q_BAR);
        assertQuery ("not(//foo intersect //bar)", Q_FOO, Q_BAR);
    }
    
    @Test public void testExists () throws Exception {
        assertQuery ("exists(//foo)", "lux_elt_name_ms:foo");//, XPathQuery.MINIMAL, ValueType.BOOLEAN);
    }
    
    public static void assertQuery (String xpath, String luq, boolean isMinimal, ValueType valueType) {
        assertQuery (xpath, luq); //, isMinimal ? XPathQuery.MINIMAL : 0, valueType);
    }
    
    public static void assertQuery (String xpath, String ... queries) {
        Saxon saxon = new Saxon();
        SaxonExpr expr = saxon.compile(xpath);
        AbstractExpression ex = saxon.getTranslator().exprFor(expr.getXPathExecutable().getUnderlyingExpression().getInternalExpression());
        SearchExtractor extractor = new SearchExtractor();
        ex.accept(extractor);
        assertEquals ("wrong number of queries for " + xpath, queries.length, extractor.queries.size());
        for (int i = 0; i < queries.length; i++) {
            assertEquals (queries[i], extractor.queries.get(i));
        }      
        // FIXME: check minimality constraints??
        /*
        boolean isMinimal = (facts & XPathQuery.MINIMAL) != 0;
        assertEquals ("isMinimal was not " + isMinimal + " for xpath " + xpath,
                isMinimal, expr.getXPathQuery().isMinimal());
        assertEquals ("query is COUNTING", (facts & XPathQuery.COUNTING) != 0, expr.getXPathQuery().isFact(XPathQuery.COUNTING));
        if (valueType != null)
            assertEquals(valueType, expr.getXPathQuery().getResultType());
        */
    }
    
    static class SearchExtractor extends ExpressionVisitorBase {
        ArrayList<String> queries = new ArrayList<String>();
        
        public void visit (FunCall funcall) {
            if (funcall.getQName().equals (FunCall.luxSearchQName)) {
                queries.add(((LiteralExpression)funcall.getSubs()[0]).getValue().toString());
            }
        }
        
    }
}
