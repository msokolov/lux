package lux.index.field;

import static org.junit.Assert.*;
import lux.Evaluator;
import lux.IndexTestSupport;
import lux.QueryStats;
import lux.XdmResultSet;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.apache.lucene.store.RAMDirectory;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests for features related to XPathFields.
 */
public class TinyBinaryStoreTest {
    
    private static IndexTestSupport indexTestSupport;
    private static Evaluator eval;
    
    @BeforeClass
    public static void init() throws Exception {
        XmlIndexer indexer = new XmlIndexer(IndexConfiguration.DEFAULT_OPTIONS | IndexConfiguration.STORE_TINY_BINARY);
        indexTestSupport = new IndexTestSupport(indexer, new RAMDirectory());
        indexTestSupport.indexAllElements("lux/reader-test.xml");
        indexTestSupport.reopen(); // commit, make the commit visible
        eval = indexTestSupport.makeEvaluator();
    }
    
    @Before
    public void setup() {
    	eval.setQueryStats(new QueryStats());
    }
    
    @Test
    public void testTinyBinaryField () {
    	assertEquals ("test", getStringResult ("/test/@id"));
    }

    @Test
    public void testTinyBinaryURI () {
    	assertEquals ("lux://lux/reader-test.xml", getStringResult ("/test/root()/base-uri()"));
    	assertEquals ("lux://lux/reader-test.xml", getStringResult ("/test/base-uri()"));
    }
    
    // return null if no results, else concatenate the string values of all the results,
    // separated by a single space
    private String getStringResult (String query) {
        XdmResultSet result = eval.evaluate(query);
        if (! result.getErrors().isEmpty()) {
            fail(result.getErrors().get(0).getMessage());
        }
        XdmSequenceIterator iter = result.getXdmValue().iterator();
        if (!iter.hasNext()) {
            return null;
        }
        StringBuilder buf = new StringBuilder ();
        buf.append (iter.next().getStringValue());
        while (iter.hasNext()) {
            buf.append (' ');
            buf.append (iter.next().getStringValue());
        }
        return buf.toString();
    }

}
