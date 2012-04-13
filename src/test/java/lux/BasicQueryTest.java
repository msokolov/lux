package lux;

import static org.junit.Assert.assertFalse;
import lux.api.ValueType;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.junit.Test;

/**
 * Tests the parsing of XPath expressions and the generation
 * of a supporting Lucene query using only node name indexes.
 * 
 * TODO: add some tests with empty steps like self::* and self::node() axis - these should maintain
 * minimality.
 */
public abstract class BasicQueryTest {
    
    private static final String Q_ATTR = "lux_att_name_ms:attr";
    private static final String Q_BAR = "lux_elt_name_ms:bar";
    private static final String Q_FOO_BAR = "+lux_elt_name_ms:foo +lux_elt_name_ms:bar";
    private static final String Q_FOO = "lux_elt_name_ms:foo";

    /**
     * asserts that the given xpath generates the lucene query string, and is or is not
     * proven to be minimal, and returns the given type
     * 
     * @param expr the expression to be tested
     * @param luq the expected lucene query string
     * @param isMinimal whether the query returns the expression results directly
     * @param valueType the expected return type of the expression
     */
    public abstract void assertQuery (String expr, String luq, boolean isMinimal, ValueType valueType) throws Exception;

    protected static final String MATCH_ALL = new MatchAllDocsQuery().toString();
    
    @Test public void testEmpty () throws Exception {

        try {
            assertQuery ("", null, true, ValueType.DOCUMENT);
            assertFalse ("expected syntax error", true);
        } catch (Exception e) { }

        try {
            assertQuery (null, null, true, ValueType.DOCUMENT); // NPE?
            assertFalse ("expected NPE", true);
        } catch (NullPointerException e) {}
    }
    
    @Test public void testParseError () throws Exception {
        try {
            assertQuery ("bad xpath here", null, false, null);
            assertFalse ("expected syntax error", true);
        } catch (Exception ex) { }
    }

    @Test public void testMatchAll () throws Exception {
        // Can you have an XML document with no elements?  I don't think so
        assertQuery ("*", MATCH_ALL, true, ValueType.ELEMENT);
        assertQuery ("node()", MATCH_ALL, true, ValueType.NODE);
        assertQuery ("/*", MATCH_ALL, true, ValueType.ELEMENT);
        assertQuery ("/node()", MATCH_ALL, true, ValueType.NODE);
        assertQuery ("/self::node()", MATCH_ALL, true, ValueType.DOCUMENT);
        assertQuery ("self::node()", MATCH_ALL, true, ValueType.DOCUMENT);
    }
    
    @Test public void testSlash() throws Exception {
        assertQuery ("/", MATCH_ALL, true, ValueType.DOCUMENT);
    }
    
    @Test public void testElementNameTest() throws Exception {
        assertQuery ("foo", Q_FOO, false, ValueType.ELEMENT); 
    }
    
    @Test public void testElementPredicate() throws Exception {
        assertQuery ("(/)[.//foo]", Q_FOO, true, ValueType.DOCUMENT); 
    }

    @Test public void testElementPaths () throws Exception {
       
        assertQuery ("//foo", Q_FOO, true, ValueType.ELEMENT);

        assertQuery ("/*/foo", Q_FOO, false, ValueType.ELEMENT);
        
        assertQuery ("/foo//*", Q_FOO, false, ValueType.ELEMENT);

        assertQuery ("/foo", Q_FOO, false, ValueType.ELEMENT);
        
        assertQuery ("foo/text()", Q_FOO, false, null);
    }

    @Test public void testAttributePaths () throws Exception {
        // FIXME: compute minimality properly for attributes
        
        assertQuery ("//*/@attr", Q_ATTR, true, ValueType.ATTRIBUTE);
        
        assertQuery ("//node()/@attr", Q_ATTR, true, ValueType.ATTRIBUTE);
    }
    
    @Test public void testAttributePredicates () throws Exception {
        assertQuery ("//*[@attr]", Q_ATTR, true, ValueType.ELEMENT);

        assertQuery ("(/)[.//*/@attr]", Q_ATTR, true, ValueType.DOCUMENT);        
    }

    @Test public void testElementAttributePaths () throws Exception {
        
        assertQuery ("foo/@id", "+lux_elt_name_ms:foo +lux_att_name_ms:id", false, ValueType.ATTRIBUTE);

        assertQuery ("foo/@*", Q_FOO, false, ValueType.ATTRIBUTE);
    }

    @Test public void testTwoElementPaths () throws Exception {
        
        assertQuery ("foo/bar", Q_FOO_BAR, false, ValueType.ELEMENT);

        assertQuery ("foo//bar", Q_FOO_BAR, false, ValueType.ELEMENT);
    }
    
    @Test public void testTwoElementPredicates () throws Exception {
        assertQuery ("(/)[.//foo][.//bar]", Q_FOO_BAR, true, ValueType.DOCUMENT);
    }
    
    @Test public void testUnion () throws Exception {
        assertQuery ("foo|bar", "lux_elt_name_ms:foo lux_elt_name_ms:bar", false, ValueType.ELEMENT);        

        assertQuery ("//foo|//bar", "lux_elt_name_ms:foo lux_elt_name_ms:bar", true, ValueType.ELEMENT);
    }
    
    @Test public void testPositionalPredicate () throws Exception {
        assertQuery ("foo/bar[1]", Q_FOO_BAR, false, ValueType.ELEMENT);
        
        assertQuery ("//bar[1]", Q_BAR, false, ValueType.ELEMENT);
    }

    @Test public void testMultiElementPaths () throws Exception {
        assertQuery ("foo/title | bar/title | baz/title", 
                     "((+lux_elt_name_ms:foo +lux_elt_name_ms:title) (+lux_elt_name_ms:bar +lux_elt_name_ms:title)) (+lux_elt_name_ms:baz +lux_elt_name_ms:title)", false, ValueType.ELEMENT);
    }

    @Test public void testElementValueNoPath () throws Exception {
        assertQuery ("foo[.='content']", Q_FOO, false, ValueType.ELEMENT);
    }
    
    @Test public void testElementStringValue () throws Exception {
        assertQuery ("foo[bar='content']", Q_FOO_BAR, false, ValueType.ELEMENT);
    }
    
    @Test public void testElementValue () throws Exception {
        assertQuery ("/foo[.='content']", Q_FOO, false, ValueType.ELEMENT);

        assertQuery ("/foo[bar='content']", Q_FOO_BAR, false, ValueType.ELEMENT);

        assertQuery ("//foo[.='content']", Q_FOO, false, ValueType.ELEMENT);

        assertQuery ("//foo[bar='content']", Q_FOO_BAR, false, ValueType.ELEMENT);

    }
    
    @Test public void testAncestorOrSelf () throws Exception {
        assertQuery ("/ancestor-or-self::node()", "*:*", true, ValueType.DOCUMENT);
    }
    
    @Test public void testSelf () throws Exception {
        assertQuery ("/self::node()", "*:*", true, ValueType.DOCUMENT);
    }
    
    @Test public void testAtomicResult () throws Exception {
        assertQuery ("number(/doc/test[1])", "+lux_elt_name_ms:doc +lux_elt_name_ms:test", false, ValueType.ATOMIC);
    }

}