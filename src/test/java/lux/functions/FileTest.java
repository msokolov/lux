package lux.functions;

import static org.junit.Assert.*;
import lux.Evaluator;
import lux.IndexTestSupport;
import lux.XdmResultSet;

import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Test the implementation of lux:search() as a URI "resolved" by fn:collection()
 */
public class FileTest {
    
    private static IndexTestSupport indexTestSupport;
    private static Evaluator eval;
    
    @BeforeClass
    public static void setup() throws Exception {
        indexTestSupport = new IndexTestSupport("lux/reader-test.xml");
        eval = indexTestSupport.makeEvaluator();
    }
    
    @Test
    public void testIsDir() throws Exception {
    	XdmResultSet result = eval.evaluate ("declare namespace file='http://expath.org/ns/file';" +
    			"(file:is-dir('.'), file:is-dir('xxx'), file:is-dir('./src/test/resources/conf/schema.xml'))");
    	assertEquals ("true false false", result.getXdmValue().getUnderlyingValue().getStringValue());
    }

    @Test
    public void testExists() throws Exception {
    	XdmResultSet result = eval.evaluate ("declare namespace file='http://expath.org/ns/file';" +
    			"(file:exists('.'), file:exists('xxx'), file:exists('./src/test/resources/conf/schema.xml'))");
    	assertEquals ("true false true", result.getXdmValue().getUnderlyingValue().getStringValue());
    }

    @Test
    public void testListFiles() throws Exception {
    	XdmResultSet result = eval.evaluate ("declare namespace file='http://expath.org/ns/file';" +
    			"(file:list('./src/test/resources/conf'))");
    	assertEquals ("schema.xml solrconfig.xml", result.getXdmValue().getUnderlyingValue().getStringValue());
    }


}
