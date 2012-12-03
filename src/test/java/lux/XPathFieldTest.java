package lux;

import static org.junit.Assert.assertEquals;

import java.util.Iterator;

import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.document.Field.Store;

import lux.XdmResultSet;
import lux.index.XmlIndexer;
import lux.index.field.XPathField;
import lux.index.field.FieldDefinition.Type;

import net.sf.saxon.s9api.XdmItem;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * Tests for features related to XPathFields.
 */
public class XPathFieldTest {
    
    private static RAMDirectory dir;
    private static IndexTestSupport indexTestSupport;

    @BeforeClass
    public static void setup () throws Exception {
        XmlIndexer indexer = new XmlIndexer ();
        indexer.getConfiguration().addField(new XPathField<Integer>("doctype", "name(/*)", null, Store.NO, Type.STRING));
        
        dir = new RAMDirectory();
        indexTestSupport = new IndexTestSupport (indexer, dir);
    }

    @AfterClass
    public static void cleanup () throws Exception {
        indexTestSupport.close(); // indexwriter close?
        dir.close();
    }

    /**
     * test to ensure that ordering by lux:key() works
     *
     * function lux:key(name as xs:string) returning item
     * is a special function for use that accepts the name of a lucene field. 
     * As an order by expression, it causes the enclosing sequence to be ordered
     * by the value of the given field. 
     */
    @Test
    public void testOrderByKey () throws Exception {
        Evaluator eval = indexTestSupport.makeEvaluator();
        final String PITHY_QUOTE = "\"There are more things in heaven and earth, Horatio\""; // , than are dreamt of in your philosophy

        String xquery = "for $doc in lux:search('" + PITHY_QUOTE + "') return $doc/*/name()";
        XdmResultSet baseline = eval.evaluate (xquery);
        // should be ordered in document creation order, which corresponds
        // to document order from the original hamlet.xml:
        // PLAY, ACT, SCENE, SPEECH, LINE
        assertEquals (5, baseline.size());
        Iterator<XdmItem> iter = baseline.iterator();
        assertEquals ("PLAY", iter.next().getStringValue());
        assertEquals ("ACT", iter.next().getStringValue());
        assertEquals ("SCENE", iter.next().getStringValue());
        assertEquals ("SPEECH", iter.next().getStringValue());
        assertEquals ("LINE", iter.next().getStringValue());

        xquery = "for $doc in lux:search('" + PITHY_QUOTE + "')" + 
            " order by lux:key($doc,'doctype') return $doc/*/name()";
        XdmResultSet results = eval.evaluate (xquery);
        // should be ordered by the names of the root elements:
        // ACT, LINE, PLAY, SCENE, SPEECH
        assertEquals (5, results.size());
        iter = results.iterator();
        assertEquals ("ACT", iter.next().getStringValue());
        assertEquals ("LINE", iter.next().getStringValue());
        assertEquals ("PLAY", iter.next().getStringValue());
        assertEquals ("SCENE", iter.next().getStringValue());
        assertEquals ("SPEECH", iter.next().getStringValue());
    }

    // TODO: test empty greatest / empty least
}
