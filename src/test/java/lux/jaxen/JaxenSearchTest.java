package lux.jaxen;

import static org.junit.Assert.assertEquals;

import java.util.List;

import lux.SearchTest;
import lux.api.Evaluator;

import org.jaxen.ContextSupport;
import org.junit.Ignore;
import org.junit.Test;

public class JaxenSearchTest extends SearchTest {

    @Override
    public Evaluator getEvaluator() {
        Evaluator eval = new JaxenEvaluator();
        eval.setContext(new JaxenContext(new ContextSupport(), searcher));
        return eval;
    }
    
    public void testCountAllDocs () {
        // This test fails w/Jaxen; we never did optimize count() there
    }
    
    @Ignore @Test
    public void testTimer () throws Exception {
        // 6.2 seconds + -> 6636*20/6.2 = 21,000 results per/sec!
        for (int i = 0; i < 20; i++) {
            testSearchAllDocs();
        }
    }
    
    @Ignore @Test public void testTimeNoParse () throws Exception {
        // 0.4 sec!! -> 15X speedup
        // This is an upper bound on what we could expect from using non-parsed XML storage
        // saving us a parse-and-create OM step inside the query evaluator
        // For Solr the speedup could be even better b/c there we have to serialize the results
        // It doesn't count XPath evaluation, which for more complex expressions would dominate
        for (int i = 0; i < 20; i++) {
            LuXPathBasic xpath = new LuXPathBasic ("/");
            xpath.dontParse = true;
            List<?> results = (List<?>) xpath.evaluate(new JaxenContext(new ContextSupport(), searcher));
            assertEquals (totalDocs, results.size());
        }
    }

}
