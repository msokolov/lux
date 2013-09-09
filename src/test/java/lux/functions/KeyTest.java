package lux.functions;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;

import lux.Evaluator;
import lux.IndexTestSupport;
import lux.XdmResultSet;
import lux.index.XmlIndexer;
import lux.index.field.FieldDefinition.Type;
import lux.index.field.XPathField;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmItem;

import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.RAMDirectory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class KeyTest {
    
    private static IndexTestSupport index;
    
    @BeforeClass
    public static void setup () throws Exception {
        XmlIndexer indexer = new XmlIndexer();
        index = new IndexTestSupport(indexer, new RAMDirectory());
        indexer.getConfiguration().addField(new XPathField("doctype_s", "name(/*)", null, Store.YES, Type.STRING));
        indexer.getConfiguration().addField(new XPathField("timestamp", "xs:integer((current-dateTime() - xs:dateTime('1970-01-01T00:00:00-00:00')) div xs:dayTimeDuration('PT1S'))", null, Store.YES, Type.INT));
        index.indexAllElements("lux/reader-test.xml");

        //index= new IndexTestSupport ("lux/reader-test.xml", indexer, new RAMDirectory());
    }
    
    @AfterClass
    public static void tearDown() throws Exception {
        index.close();
    }

    @Test
    public void testKeyNotStored () throws Exception {
        String[] terms = getValues("collection()/lux:key('lux_elt_name')");
        assertEquals ("got results for non-stored field", 0, terms.length);
    }
    
    @Test
    public void testKeyUndeclared () throws Exception {
        String[] terms = getValues("collection()/lux:key('timestamp')");
        assertEquals (5, terms.length);
        Long t0 = Long.parseLong(terms[0]);
        Long t1 = Long.parseLong(terms[1]);
        assertTrue ("time " + t1 + " before " + t0, t1 >= t0);
        assertTrue ("timestamp in the future??", t0 <= System.currentTimeMillis()/1000);
        assertTrue ("timestamp in the distant past??", t0 > System.currentTimeMillis()/1000 - 10);
    }
    
    @Test
    public void testKey () throws Exception {

        String[] values = getValues("count(collection())");
        assertEquals ("5", values[0]);
        
        String[] terms = getValues("lux:key('lux_uri', collection()[1])");
        assertArrayEquals (new String[] {"/lux/reader-test.xml"}, terms);
        
        terms = getValues("collection()[1]/lux:key('doctype_s')");
        assertArrayEquals (new String[] {"test"}, terms);
        
        terms = getValues("collection()/lux:key('doctype_s')");
        assertArrayEquals (new String[] {"test", "title", "entities", "entities", "token"}, terms);
    }

    private String[] getValues(String xquery) throws CorruptIndexException, LockObtainFailedException, IOException {
        Evaluator eval = index.makeEvaluator();
        XQueryExecutable exec = eval.getCompiler().compile(xquery);
        XdmResultSet results = eval.evaluate(exec);
        ArrayList<String> terms = new ArrayList<String>();
        for (XdmItem term : results) {
            terms.add (term.getStringValue());
        }
        return terms.toArray(new String[terms.size()]);
    }    
}
