package lux.saxon;

import static org.junit.Assert.assertEquals;
import lux.XPathQuery;
import lux.api.ValueType;

import org.junit.Test;

/*
 * test XPath 2.0 parsing, query generation and analysis for a variety of expressions
 */
public class QueryTest {

    private static final String Q_FOO = "lux_elt_name_ms:foo";
    private static final String Q_BAR_FOO = "lux_elt_name_ms:bar lux_elt_name_ms:foo";
    private static final String Q_FOO_BAR = "lux_elt_name_ms:foo lux_elt_name_ms:bar";

    @Test public void testRoot() {
        assertQuery ("//foo/root()", Q_FOO, true, ValueType.DOCUMENT);
        
        assertQuery ("foo/root()", Q_FOO, false, ValueType.DOCUMENT);
        
        assertQuery ("document-node()[.//foo]", Q_FOO, true, ValueType.DOCUMENT);
        
        assertQuery ("document-node()[foo]", Q_FOO, false, ValueType.DOCUMENT);

        assertQuery ("node()", "*:*", true, ValueType.NODE);
        
        assertQuery ("node()[.//foo]", Q_FOO, true, ValueType.NODE);
        
        assertQuery ("//foo/root()|//bar/root()", "lux_elt_name_ms:foo lux_elt_name_ms:bar", true, ValueType.DOCUMENT);
    }
    
    // FIXME - we need to distinguish count (exact expr) from count(minimal expr)
    @Test public void testCount() {
        assertQuery ("count (/)", "*:*", XPathQuery.COUNTING | XPathQuery.MINIMAL, ValueType.ATOMIC);
        
        assertQuery ("count (//foo)", Q_FOO, XPathQuery.COUNTING | XPathQuery.MINIMAL, ValueType.ATOMIC);
        
        assertQuery ("count (/foo/bar)", null, XPathQuery.COUNTING, ValueType.ATOMIC);
        
        assertQuery ("count (//foo[3])", null, XPathQuery.COUNTING, ValueType.ATOMIC);
        
        assertQuery ("/descendant::foo[position() = (count(//foo) div 2)]", null, 0, ValueType.ELEMENT);
    }
    
    @Test public void testSumCounts() {
        
        assertQuery ("count (//foo | //bar)", Q_BAR_FOO, XPathQuery.COUNTING | XPathQuery.MINIMAL, ValueType.ATOMIC);

        assertQuery ("count (//foo) + count(//bar)", Q_FOO_BAR, XPathQuery.COUNTING | XPathQuery.MINIMAL, ValueType.ATOMIC);
    }
    
    @Test public void testNot () throws Exception {
        assertQuery ("not(//foo)", "+lux_elt_name_ms:foo", XPathQuery.MINIMAL, ValueType.BOOLEAN);        
    }
    
    @Test public void testExists () throws Exception {
        assertQuery ("exists(//foo)", "lux_elt_name_ms:foo", XPathQuery.MINIMAL, ValueType.BOOLEAN);
    }
    
    public static void assertQuery (String xpath, String luq, boolean isMinimal, ValueType valueType) {
        assertQuery (xpath, luq, isMinimal ? XPathQuery.MINIMAL : 0, valueType);
    }
    
    public static void assertQuery (String xpath, String luq, int facts, ValueType valueType) {
        Saxon saxon = new Saxon();
        SaxonExpr expr = saxon.compile(xpath);
        if (luq != null) {
            String query = expr.getSearchQuery ();
            assertEquals (xpath, luq, query);
        }
        boolean isMinimal = (facts & XPathQuery.MINIMAL) != 0;
        assertEquals ("isMinimal was not " + isMinimal + " for xpath " + xpath,
                isMinimal, expr.getXPathQuery().isMinimal());
        assertEquals ("query is COUNTING", (facts & XPathQuery.COUNTING) != 0, expr.getXPathQuery().isFact(XPathQuery.COUNTING));
        if (valueType != null)
            assertEquals(valueType, expr.getXPathQuery().getResultType());
    }
}
