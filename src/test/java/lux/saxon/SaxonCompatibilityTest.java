package lux.saxon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import lux.Evaluator;
import lux.exception.LuxException;
import net.sf.saxon.s9api.XQueryExecutable;

import org.junit.Test;

public class SaxonCompatibilityTest {
    
    @Test
    public void testSaxonExtension () {
        Evaluator eval = new Evaluator();
        try {
            XQueryExecutable xquery = eval.getCompiler().compile("saxon:serialize(<foo />)");
            assertEquals ("<foo />", eval.evaluate(xquery));
        } catch (LuxException e) {
            assertFalse (eval.getCompiler().isSaxonLicensed());
        }
    }
}
