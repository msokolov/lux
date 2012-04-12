package lux.saxon;

import static org.junit.Assert.assertEquals;
import lux.BasicQueryTest;
import lux.XPathQuery;
import lux.api.ValueType;
import lux.xpath.AbstractExpression;
import lux.xpath.PathOptimizer;

import org.junit.Test;

public class SaxonTranslatorQueryTest extends BasicQueryTest {

    public void assertQuery (String xpath, String luq, boolean isMinimal, ValueType valueType) {
        Saxon saxon = new Saxon();
        SaxonExpr expr = saxon.compile(xpath);
        AbstractExpression aex = saxon.getTranslator().exprFor(expr.getXPathExecutable().getUnderlyingExpression().getInternalExpression());
        PathOptimizer optimizer = new PathOptimizer();
        aex.accept(optimizer);
        XPathQuery query =  optimizer.getQuery ();
        if (luq != null) {
            assertEquals (xpath, luq, query.toString());
        }
        assertEquals ("isMinimal was not " + isMinimal + " for xpath " + xpath,
                isMinimal, query.isMinimal());
        if (valueType != null)
            assertEquals(valueType, query.getResultType());
    }
    
    // Saxon switches the operand order
    @Test public void testUnion () throws Exception {
        assertQuery ("foo|bar", "lux_elt_name_ms:foo lux_elt_name_ms:bar", false, ValueType.ELEMENT);

        assertQuery ("//foo|//bar", "lux_elt_name_ms:foo lux_elt_name_ms:bar", true, ValueType.ELEMENT);
    }
    
    @Test public void testSequence () throws Exception {
        assertQuery ("(foo,bar,baz)", "lux_elt_name_ms:foo (lux_elt_name_ms:bar lux_elt_name_ms:baz)", false, ValueType.ELEMENT);
    }
    
    @Test public void testMatchNone () throws Exception {
        // Saxon detects that this will never match anything and we therefore don't require a query
        // MINIMAL is false since there are simply no facts at all about a non-existent query
        assertQuery ("/self::*", "", false, ValueType.VALUE);
        // Here Saxon thinks there might be a non-document context and a match is possible
        assertQuery ("self::*", MATCH_ALL, true, ValueType.ELEMENT);
    }
    
    @Test public void testAncestor () throws Exception {
        // matches nothing
        assertQuery ("/ancestor::node()", "", false, ValueType.VALUE);
    }
    
    
    
}
