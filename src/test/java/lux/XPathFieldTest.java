package lux;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Iterator;

import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.CorruptIndexException;

import lux.XdmResultSet;
import lux.index.XmlIndexer;
import lux.index.field.XPathField;
import lux.index.field.FieldDefinition.Type;

import net.sf.saxon.s9api.XdmItem;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;


/**
 * Tests for features related to XPathFields.
 */
public class XPathFieldTest {
    
    private static RAMDirectory dir;
    private static IndexTestSupport indexTestSupport;
    private Evaluator eval;

    @BeforeClass
    public static void setup () throws Exception {
        XmlIndexer indexer = new XmlIndexer ();
        indexer.getConfiguration().addField(new XPathField<Integer>("doctype", "name(/*)", null, Store.NO, Type.STRING));
        indexer.getConfiguration().addField(new XPathField<Integer>("doctype-stored", "name(/*)", null, Store.YES, Type.STRING));
        
        dir = new RAMDirectory();
        indexTestSupport = new IndexTestSupport (indexer, dir);
    }

    @AfterClass
    public static void cleanup () throws Exception {
        indexTestSupport.close(); // indexwriter close?
        dir.close();
    }

    @Before
    public void init () throws CorruptIndexException, LockObtainFailedException, IOException {
        eval = indexTestSupport.makeEvaluator();
    }
    
    @Test
    public void testFieldValues () throws Exception {
      // node argument
      assertEval ("PLAY", "lux:field-values('doctype-stored', /PLAY)");

      // node from context item
      assertEval ("PLAY", "/PLAY/lux:field-values('doctype-stored')");

      // no value for empty context item
      assertEval ("", "lux:field-values('doctype-stored')");

      // values of field that's not stored can't be retrieved 
      assertEval ("", "/PLAY/lux:field-values('doctype')");
    }
    
    private void assertEval (String expectedResult, String xquery) {
        assertEquals (expectedResult, evalOne (xquery));
    }
    
    private String evalOne (String expr) {
        Iterator<XdmItem> iterator = eval.evaluate (expr).iterator();
        if (! iterator.hasNext()) {
            return "";
        }
        return iterator.next().getStringValue();
    }
    
    /**
     * test to ensure that ordering by lux:key() works
     */
    @Test
    public void testOrderByKey () throws Exception {
        final String PITHY_QUOTE = "\"There are more things in heaven and earth, Horatio\""; // , than are dreamt of in your philosophy

        String xquery = "for $doc in lux:search('" + PITHY_QUOTE + "') return $doc/*/name()";
        // should be ordered in document creation order, which corresponds
        // to document order from the original hamlet.xml:
        // PLAY, ACT, SCENE, SPEECH, LINE
        assertResultSequence (xquery, "PLAY", "ACT", "SCENE", "SPEECH", "LINE");

        xquery = "for $doc in lux:search('" + PITHY_QUOTE + "')" + 
            " order by lux:field-values('doctype', $doc) return $doc/*/name()";
        // should be ordered by the names of the root elements:
        // ACT, LINE, PLAY, SCENE, SPEECH
        assertResultSequence (xquery,  "ACT", "LINE", "PLAY", "SCENE", "SPEECH");
    }
    
    private void assertResultSequence (String query, String ... results) {
        XdmResultSet resultSet = eval.evaluate(query); 
        Iterator<XdmItem> iter = resultSet.iterator();
        for (String result : results) {
            assertEquals (result, iter.next().getStringValue());
        }
        assertFalse (iter.hasNext());
    }

    // TODO: test empty greatest / empty least
}
