package lux.functions;

import static org.junit.Assert.*;
import lux.Evaluator;
import lux.IndexTestSupport;
import lux.XdmResultSet;

import net.sf.saxon.s9api.XdmSequenceIterator;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test the implementation of lux:search() as a URI "resolved" by fn:collection()
 */
public class CollectionTest {
    
    private static IndexTestSupport indexTestSupport;
    private static Evaluator eval;
    
    @BeforeClass
    public static void setup() throws Exception {
        indexTestSupport = new IndexTestSupport("lux/reader-test.xml");
        eval = indexTestSupport.makeEvaluator();
    }
    
    @Test
    public void testSearchSingleWord() throws Exception {
        XdmResultSet result = eval.evaluate("collection('lux:12345678')/base-uri()");
        if (result.getErrors() != null) {
            assertNull(result.getErrors().get(0).getMessage(), result.getErrors());
        }
        XdmSequenceIterator iter = result.getXdmValue().iterator();
        assertEquals ("lux://lux/reader-test.xml", iter.next().getStringValue());
        assertEquals ("lux://lux/reader-test.xml-4", iter.next().getStringValue());
        assertFalse (iter.hasNext());
    }

    @Test
    public void testElementWordSearch() throws Exception {
        XdmResultSet result = eval.evaluate("collection('lux:<token:12345678')/base-uri()");
        if (result.getErrors() != null) {
            assertNull(result.getErrors().get(0).getMessage(), result.getErrors());
        }
        XdmSequenceIterator iter = result.getXdmValue().iterator();
        assertEquals ("lux://lux/reader-test.xml", iter.next().getStringValue());
        assertEquals ("lux://lux/reader-test.xml-4", iter.next().getStringValue());
        assertFalse (iter.hasNext());
    }

}
