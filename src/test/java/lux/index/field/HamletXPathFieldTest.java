package lux.index.field;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Iterator;

import javax.xml.transform.TransformerException;

import lux.Evaluator;
import lux.IndexTestSupport;
import lux.MultiThreadedRunner;
import lux.XdmResultSet;
import lux.index.XmlIndexer;
import lux.index.field.FieldDefinition.Type;
import net.sf.saxon.s9api.XdmItem;

import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.RAMDirectory;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for features related to XPathFields using the hamlet.xml dataset.
 */
@RunWith (MultiThreadedRunner.class)
public class HamletXPathFieldTest {
    
    private static RAMDirectory dir;
    private static IndexTestSupport indexTestSupport;
    private Evaluator eval;

    @BeforeClass
    public static void setup () throws Exception {
        XmlIndexer indexer = new XmlIndexer ();
        indexer.getConfiguration().addField(new XPathField("doctype", "name(/*)", null, Store.NO, Type.STRING));
        indexer.getConfiguration().addField(new XPathField("doctype-stored", "name(/*)", null, Store.YES, Type.STRING));
        indexer.getConfiguration().addField(new XPathField("title", "/*/TITLE", null, Store.YES, Type.STRING));
        dir = new RAMDirectory();
        indexTestSupport = new IndexTestSupport ("lux/hamlet.xml", indexer, dir);
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
      assertEval ("PLAY", "lux:key('doctype-stored', /PLAY)");

      // node from context item
      assertEval ("PLAY", "/PLAY/lux:key('doctype-stored')");

      // no value for empty context item
      assertEval ("", "lux:key('doctype-stored')");

      // values of field that's not stored can't be retrieved 
      assertEval ("", "/PLAY/lux:key('doctype')");
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
     * tests ordering by relevance and by lux:key()
     * @throws Exception 
     */
    @Test
    public void testOrderByKey () throws Exception {
        final String PITHY_QUOTE = "\"There are more things in heaven and earth, Horatio\""; // , than are dreamt of in your philosophy

        String xquery = "for $doc in lux:search('" + PITHY_QUOTE + "') return $doc/*/name()";
        // should be ordered in *relevance* order, which will basically be ordered by length:
        assertResultSequence (xquery, "LINE", "SPEECH", "SCENE", "ACT", "PLAY");

        xquery = "for $doc in lux:search('" + PITHY_QUOTE + "')" + 
            " order by lux:key('doctype', $doc) return $doc/*/name()";
        // should be ordered by the names of the root elements:
        // ACT, LINE, PLAY, SCENE, SPEECH
        assertResultSequence (xquery,  "ACT", "LINE", "PLAY", "SCENE", "SPEECH");
    }
    
    @Test
    public void testOrderByEmpty () throws Exception {
        // get the titles of the first three docs in order by title, using the default "empty least":
        // these are blank
        String xquery = "subsequence(for $doc in collection() order by $doc/lux:key('title') " +
            "return string($doc/*/TITLE), 1, 3)";
        assertResultSequence (xquery, "", "", "");

        // get the first three non-empty titles in order by title, using the default "empty least":
        // this requires iterating over all the blank titles first, and discarding
        xquery = "subsequence(for $doc in collection() order by $doc/lux:key('title') " +
        		"return $doc/lux:key('title'), 1, 3)";
        assertResultSequence (xquery, "ACT I", "ACT II", "ACT III");

        // get the first three non-empty titles in order by title, using "empty greatest":
        // this requires Saxon to perform the sorting since we don't implement empty greatest using Lucene
        xquery = "subsequence(for $doc in collection() order by $doc/lux:key('title') empty greatest " +
                "return string($doc/*/TITLE), 1, 3)";
        assertResultSequence (xquery, "ACT I", "ACT II", "ACT III");
        
        // Get the first three non-empty titles in reverse order by title, using the default "empty least":
        xquery = "subsequence(for $doc in collection() order by $doc/lux:key('title') descending " +
        		"return $doc/lux:key('title'), 1, 2)";
        assertResultSequence (xquery, "The Tragedy of Hamlet, Prince of Denmark", 
                "SCENE VII.  Another room in the castle.");
    }
    
    @Test
    public void testFieldValuesNoContext () throws Exception {
        try {
            assertResultSequence ("lux:key('title')", "");
            assertFalse ("expected exception not thrown", true);
        } catch (Exception e) {
            assertTrue (e.getMessage().contains("there is no context defined"));
        }
        try {
            assertResultSequence  ("for $doc in collection() order by lux:key('title') return $doc");
            assertFalse ("expected exception not thrown", true);
        } catch (Exception e) {
            e.printStackTrace();
            assertTrue (e.getMessage().contains("there is no context defined"));
        }
    }
    
    @Test
    public void testFieldValuesNoField () throws Exception {
        // no error, just return empty sequence
        assertResultSequence ("collection()[1]/lux:key('bogus')");
        // also test that we still have backwards-compatibility support for the old name
        // of this method
        assertResultSequence ("collection()[1]/lux:key('bogus')");
    }
    
    @Test
    public void testAtomizingEmptySequence () throws Exception {
        String query = "subsequence (for $doc in collection() return string ($doc/*/TITLE), 1, 3)";
        // should return titles of the first three nodes in document order
        assertResultSequence (query, "The Tragedy of Hamlet, Prince of Denmark", "", "");
    }
    
    private void assertResultSequence (String query, String ... results) throws TransformerException {
        XdmResultSet resultSet = eval.evaluate(query);
        if (! resultSet.getErrors().isEmpty()) {
            throw resultSet.getErrors().get(0);
        }
        Iterator<XdmItem> iter = resultSet.iterator();
        for (String result : results) {
            assertTrue ("too few results returned", iter.hasNext());
            assertEquals (result, iter.next().getStringValue());
        }
        assertFalse ("query returned extra results", iter.hasNext());
    }

}
