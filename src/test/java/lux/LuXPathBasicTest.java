package lux;

import lux.LuXPath;
import lux.XPathQuery.ValueType;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.jaxen.Context;
import org.jaxen.ContextSupport;
import org.jaxen.JaxenException;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * tests the basic LuXPath implementation that indexes only element names.
 */
public class LuXPathBasicTest {

    private static final String MATCH_ALL = new MatchAllDocsQuery().toString();
    
    @Test public void testEmpty () throws Exception {

        try {
            assertQuery ("", null, true, ValueType.DOCUMENT);
            assertFalse ("expected syntax error", true);
        } catch (JaxenException e) { }

        try {
            assertQuery (null, null, true, ValueType.DOCUMENT); // NPE?
            assertFalse ("expected NPE", true);
        } catch (NullPointerException e) {}
    }

    @Test public void testMatchAll () throws Exception {
        // MatchAllDocsQuery.toString()
        // An empty document would not match...
        assertQuery ("*", MATCH_ALL, false, ValueType.ELEMENT);

        assertQuery ("/", MATCH_ALL, true, ValueType.DOCUMENT);
    }

    @Test public void testElementPaths () throws Exception {

        assertQuery ("foo", "lux_elt_name:foo", false, ValueType.ELEMENT);

        assertQuery ("//foo", "lux_elt_name:foo", true, ValueType.ELEMENT);

        assertQuery ("/*/foo", "lux_elt_name:foo", false, ValueType.ELEMENT);

        // TODO: understand what this path means!
        // assertQuery ("node()[.//foo]", "lux_elt_name:foo", true, ValueType.DOCUMENT);
        
        assertQuery ("(/)[.//foo]", "lux_elt_name:foo", true, ValueType.DOCUMENT);
        
        // In XPath 2 we could do this:
        // assertQuery ("document-node()[.//foo]", "lux_elt_name:foo", true, ValueType.DOCUMENT);
        
        // or this:
        // assertQuery ("//foo/root()", "lux_elt_name:foo", true, ValueType.DOCUMENT);
        
        assertQuery ("/foo//*", "lux_elt_name:foo", false, ValueType.ELEMENT);

        assertQuery ("foo/text()", "lux_elt_name:foo", false, ValueType.TEXT);
    }

    @Test public void testAttributePaths () throws Exception {
        // FIXME: compute minimality properly for attributes
        
        assertQuery ("//*/@attr", "lux_att_name:attr", true, ValueType.ATTRIBUTE);
        
        assertQuery ("//node()/@attr", "lux_att_name:attr", true, ValueType.ATTRIBUTE);

        assertQuery ("//*[@attr]", "lux_att_name:attr", true, ValueType.ELEMENT);

        assertQuery ("(/)[.//*/@attr]", "lux_att_name:attr", true, ValueType.DOCUMENT);
    }

    @Test public void testElementAttributePaths () throws Exception {
        
        assertQuery ("foo/@id", "+lux_elt_name:foo +lux_att_name:id", false, ValueType.ATTRIBUTE);

        assertQuery ("foo/@*", "lux_elt_name:foo", false, ValueType.ATTRIBUTE);
    }

    @Test public void testTwoElementPaths () throws Exception {
        
        assertQuery ("foo/bar", "+lux_elt_name:foo +lux_elt_name:bar", false, ValueType.ELEMENT);

        assertQuery ("foo//bar", "+lux_elt_name:foo +lux_elt_name:bar", false, ValueType.ELEMENT);
        
        assertQuery ("foo/bar[1]", "+lux_elt_name:foo +lux_elt_name:bar", false, ValueType.ELEMENT);

        assertQuery ("foo|bar", "lux_elt_name:foo lux_elt_name:bar", false, ValueType.ELEMENT);

        assertQuery ("//foo|//bar", "lux_elt_name:foo lux_elt_name:bar", true, ValueType.ELEMENT);

        //assertQuery ("//foo/root()|//bar/root()", "lux_elt_name:foo lux_elt_name:bar", true, ValueType.DOCUMENT);

        assertQuery ("(/)[.//foo][.//bar]", "+lux_elt_name:bar +lux_elt_name:foo", true, ValueType.DOCUMENT);
    }

    @Test public void testMultiElementPaths () throws Exception {
        assertQuery ("foo/title | bar/title | baz/title", 
                     "(+lux_elt_name:foo +lux_elt_name:title) ((+lux_elt_name:bar +lux_elt_name:title) (+lux_elt_name:baz +lux_elt_name:title))", false, ValueType.ELEMENT);

    }

    @Test public void testElementValue () throws Exception {
        assertQuery ("foo[.='content']", "lux_elt_name:foo", false, ValueType.ELEMENT);

        assertQuery ("foo[bar='content']", "+lux_elt_name:bar +lux_elt_name:foo", false, ValueType.ELEMENT);
    }

    public void assertQuery (String xpath, String luq, boolean isMinimal, ValueType valueType) throws JaxenException {
        LuXPath lux = new LuXPathBasic (xpath);
        Context context = new Context(new ContextSupport());
        XPathQuery query = lux.getQuery (lux.getRootExpr(), context);
        assertEquals ("isMinimal was not " + isMinimal + " for xpath " + xpath,
                      isMinimal, query.isMinimal());
        assertEquals (xpath, luq, query.getQuery().toString());
        assertEquals (xpath, valueType, query.getValueType());
    }

}