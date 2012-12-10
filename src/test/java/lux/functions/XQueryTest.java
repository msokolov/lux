package lux.functions;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Collection;

import javax.xml.transform.TransformerException;

import lux.Evaluator;
import lux.QueryContext;
import lux.XCompiler.Dialect;
import lux.XdmResultSet;
import net.sf.saxon.s9api.XQueryExecutable;

import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;

public abstract class XQueryTest {

    protected static Evaluator evaluator;

    @BeforeClass
    public static void setup() throws Exception {
        evaluator = new Evaluator(Dialect.XQUERY_1);
    }

    protected void assertXQueryFile(String result, String queryFile) throws IOException {
        assertXQueryFile (result, queryFile, null);
    }

    protected void assertXQueryFile(String result, String queryFile, String firstError) throws IOException {
        String query = IOUtils.toString(TransformTest.class.getResourceAsStream(queryFile));
        assertXQuery (result, query, firstError);
    }

    protected void assertXQuery(String result, String query) {
        assertXQuery (result, query, null);
    }

    protected void assertXQuery(String result, String query, String firstError) {
        XQueryExecutable xquery = evaluator.getCompiler().compile(query);
        XdmResultSet results = evaluator.evaluate(xquery, new QueryContext());
        if (result == null) {
            assertEquals (0, results.size());
            if (firstError != null) {
                assertEquals (firstError, results.getErrors().iterator().next().getMessage());
            } else {
                Collection<TransformerException> errors = results.getErrors();
                if (errors != null && ! errors.isEmpty()) {
                    assertFalse
                        ("got errors " + errors.iterator().next().getMessage(),
                        true);
                }
            }
            return;
        }
        assertEquals (1, results.size());
        assertEquals (result, results.iterator().next().toString());
    }

}