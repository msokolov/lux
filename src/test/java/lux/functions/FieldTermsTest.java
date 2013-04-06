package lux.functions;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;

import lux.Evaluator;
import lux.IndexTestSupport;
import lux.XdmResultSet;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmItem;

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.store.LockObtainFailedException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class FieldTermsTest {
    
    private static IndexTestSupport index;
    
    @BeforeClass
    public static void setup () throws Exception {
        index = new IndexTestSupport("lux/reader-test.xml");
    }
    
    @AfterClass
    public static void tearDown() throws Exception {
        index.close();
    }

    @Test
    public void testFieldTerms () throws Exception {
        ArrayList<String> terms = getFieldTerms("lux:field-terms('lux_elt_name')");
        assertArrayEquals (new String[] {"entities", "test", "title", "token"}, 
                terms.toArray(new String[0]));
    }
    
    @Test
    public void testAllTerms () throws Exception {
        ArrayList<String> terms = getFieldTerms("lux:field-terms()");
        assertArrayEquals(new String[] { "0", "12345678", "end", "escaped", "is", "markup", "some", "test", "that", "the", "this", "ģé"}, 
                terms.toArray(new String[0]));
    }
    
    @Test
    public void testFieldTermsStart () throws Exception {
        ArrayList<String> terms = getFieldTerms("lux:field-terms('lux_elt_name', 'ti')");
        assertArrayEquals (new String[] {"title", "token"}, terms.toArray(new String[0]));
        
        terms = getFieldTerms("lux:field-terms('lux_elt_name', 'ti')[2]");
        assertArrayEquals (new String[] {"token"}, terms.toArray(new String[0]));
        
        terms = getFieldTerms("lux:field-terms('lux_elt_name', 'zzz')");
        assertTrue (terms.isEmpty());
        
        terms = getFieldTerms("lux:field-terms((), 'zzz')");
        assertArrayEquals (new String[] {"ģé"}, terms.toArray(new String[0]));
    }

    private ArrayList<String> getFieldTerms(String xquery) throws CorruptIndexException, LockObtainFailedException, IOException {
        Evaluator eval = index.makeEvaluator();
        XQueryExecutable exec = eval.getCompiler().compile(xquery);
        XdmResultSet results = eval.evaluate(exec);
        ArrayList<String> terms = new ArrayList<String>();
        for (XdmItem term : results) {
            terms.add (term.getStringValue());
        }
        return terms;
    }    
}
