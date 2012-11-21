package lux.functions;

import java.io.IOException;

import lux.api.QueryContext;
import lux.api.ResultSet;
import lux.saxon.Saxon;
import lux.saxon.Saxon.Dialect;
import lux.xpath.QName;

import net.sf.saxon.s9api.XdmAtomicValue;

import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class TransformTest {
    
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
        QueryContext context = new QueryContext(saxon);
        context.bindVariable(new QName("external-var"), new XdmAtomicValue(10));
        ResultSet<?> results = saxon.compile(query).evaluate(context);
        assertNull ("got an unexpected error", results.getErrors());
        assertEquals ("undefined", results.iterator().next().toString());
    }
    
    private static Saxon saxon;
    
    @BeforeClass
    public static void setup () {
        saxon = new Saxon(Dialect.XQUERY_1);
    }

    private void assertXQueryFile (String result, String queryFile) throws IOException {
        assertXQueryFile (result, queryFile, null);
    }
    
    private void assertXQueryFile (String result, String queryFile, String firstError) throws IOException {
        String query = IOUtils.toString(TransformTest.class.getResourceAsStream(queryFile));
        assertXQuery (result, query, firstError);
    }

    private void assertXQuery (String result, String query) {
        assertXQuery (result, query, null);
    }
    
    private void assertXQuery (String result, String query, String firstError) {
        ResultSet<?> results = saxon.compile(query).evaluate(new QueryContext(saxon));
        if (result == null) {
            assertEquals (0, results.size());
            if (firstError != null) {
                assertEquals (firstError, results.getErrors().iterator().next().getMessage());
            } else {
                assertNull (results.getErrors());
            }
            return;
        }
        assertEquals (1, results.size());
        assertEquals (result, results.iterator().next().toString());
    }
}
