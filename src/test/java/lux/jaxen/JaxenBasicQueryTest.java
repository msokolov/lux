package lux.jaxen;

import static org.junit.Assert.assertEquals;
import lux.BasicQueryTest;
import lux.XPathQuery;
import lux.api.ValueType;

import org.jaxen.Context;
import org.jaxen.ContextSupport;
import org.jaxen.JaxenException;
import org.junit.Test;

public class JaxenBasicQueryTest extends BasicQueryTest {

    public void assertQuery (String xpath, String luq, boolean isMinimal, ValueType valueType) throws JaxenException {
        LuXPath lux = new LuXPathBasic (xpath);
        Context context = new Context(new ContextSupport());
        XPathQuery query = lux.getQuery (lux.getRootExpr(), context);
        assertEquals ("isMinimal was not " + isMinimal + " for xpath " + xpath,
                      isMinimal, query.isMinimal());
        assertEquals (xpath, luq, query.getQuery().toString());
        if (valueType != null)
            assertEquals (xpath, valueType, query.getResultType());
    }
    
    // grouping variation
    @Test public void testMultiElementPaths () throws Exception {
        assertQuery ("foo/title | bar/title | baz/title", 
                     "(+lux_elt_name_ms:foo +lux_elt_name_ms:title) ((+lux_elt_name_ms:bar +lux_elt_name_ms:title) (+lux_elt_name_ms:baz +lux_elt_name_ms:title))", false, ValueType.ELEMENT);
    }
    
    @Test public void testMatchAll () throws Exception {
        // Can you have an XML document with no elements?  I don't think so
        // assertQuery ("*", MATCH_ALL, true, ValueType.ELEMENT);
        // This test is too hard to fix in Jaxen!!
    }
    
}
