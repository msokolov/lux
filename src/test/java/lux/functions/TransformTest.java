package lux.functions;

import lux.QueryContext;
import lux.XdmResultSet;
import lux.xml.QName;

import net.sf.saxon.s9api.XdmAtomicValue;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import static org.junit.Assert.*;

public class TransformTest extends XQueryTest {
    
    @Test
    public void testBasicQuery () {
        // just make sure everything is wired up properly to run XQuery
        assertXQuery ("2", "1 + 1");
        assertXQuery ("ABC", "concat('A','B','C')");
    }
    
    @Test
    public void testTransform1 () throws Exception {
        // test a query that calls lux:transform
        assertXQueryFile ("2", "transform-1.xqy");
    }
    
    @Test
    public void testTransformError () throws Exception {
        // test a query that doesn't compile
        assertXQueryFile (null, "transform-error.xqy", "The supplied file does not appear to be a stylesheet");
    }
    
    @Test
    public void testTransformContext () throws Exception {
        // test a stylesheet that expects an external variable - attempt
        // to bind one, but it is not seen by the stylesheet
        String query = IOUtils.toString(TransformTest.class.getResourceAsStream("transform-context.xqy"));
        QueryContext context = new QueryContext();
        context.bindVariable(new QName("external-var"), new XdmAtomicValue(10));
        XdmResultSet results = evaluator.evaluate(evaluator.getCompiler().compile(query), context);
        assertNull ("got an unexpected error", results.getErrors());
        assertEquals ("undefined", results.iterator().next().toString());
    }
}
