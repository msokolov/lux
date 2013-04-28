package lux.functions;

import static org.junit.Assert.*;

import java.io.File;
import java.util.HashSet;

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
        if (! result.getErrors().isEmpty()) {
            assertEquals(result.getErrors().get(0).getMessage(), 0, result.getErrors().size());
        }
        XdmSequenceIterator iter = result.getXdmValue().iterator();
        assertEquals ("lux://lux/reader-test.xml", iter.next().getStringValue());
        assertEquals ("lux://lux/reader-test.xml-4", iter.next().getStringValue());
        assertFalse (iter.hasNext());
    }

    @Test
    public void testElementWordSearch() throws Exception {
        XdmResultSet result = eval.evaluate("collection('lux:<token:12345678')/base-uri()");
        if (! result.getErrors().isEmpty()) {
            assertNull(result.getErrors().get(0).getMessage(), result.getErrors());
        }
        XdmSequenceIterator iter = result.getXdmValue().iterator();
        assertEquals ("lux://lux/reader-test.xml", iter.next().getStringValue());
        assertEquals ("lux://lux/reader-test.xml-4", iter.next().getStringValue());
        assertFalse (iter.hasNext());
    }
    
    @Test
    public void testParseException() throws Exception {
        XdmResultSet result = eval.evaluate("collection('lux:cat AND')/base-uri()");
        assertTrue(! result.getErrors().isEmpty());
        assertEquals("Failed to parse query: cat AND", result.getErrors().get(0).getMessage());
    }
    
    @Test
    public void testEmptyQuery() throws Exception {
        XdmResultSet result = eval.evaluate("collection('lux:<<<<')/base-uri()");
        assertTrue(result.getErrors().isEmpty());
        assertEquals(0, result.getXdmValue().size());
    }
    
    @Test
    public void testDefaultCollection () throws Exception {
    	// when the arg to collection does *not* begin with lux: fall back to the default
    	// (file system) resolver.  This must be a directory containing *only* XML files
    	XdmResultSet result = eval.evaluate("collection('src/test/resources/conf')/base-uri()");
        if (! result.getErrors().isEmpty()) {
        	result.getErrors().get(0).printStackTrace();
        	assertNull(result.getErrors().get(0).getMessage(), result.getErrors());
        }
        String pwd = System.getProperty("user.dir").replace('\\', '/');
        String prefix;
        if (File.separatorChar == '\\') {
            prefix = "file:/";
        } else {
            prefix = "file:";
        }
        HashSet<String> expected = new HashSet<String> ();
        expected.add(prefix + pwd + "/src/test/resources/conf/schema.xml");
        expected.add(prefix + pwd + "/src/test/resources/conf/solrconfig.xml");
        HashSet<String> files = new HashSet<String> ();
        XdmSequenceIterator iter = result.getXdmValue().iterator();
        while (iter.hasNext()) {
            String filename = iter.next().getStringValue();
            files.add(filename);
        }
        assertEquals (expected, files); 
    }
    
    // No need to test collection() with no args here - it's tested all over the place already

}
