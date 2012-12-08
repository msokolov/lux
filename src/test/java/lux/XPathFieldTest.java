package lux;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Iterator;

import javax.xml.transform.TransformerException;

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
        indexer.getConfiguration().addField(new XPathField<String>("doctype", "name(/*)", null, Store.NO, Type.STRING));
        indexer.getConfiguration().addField(new XPathField<String>("doctype-stored", "name(/*)", null, Store.YES, Type.STRING));
        indexer.getConfiguration().addField(new XPathField<String>("title", "/*/TITLE", null, Store.YES, Type.STRING));
        // TODO: test integer fields
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
    
    @Test
    public void testOrderByEmpty () throws Exception {
        // get the titles of the first three docs in order by title, using the default "empty least":
        // these are blank
        String xquery = "subsequence(for $doc in collection() order by $doc/lux:field-values('title') " +
            "return string($doc/*/TITLE), 1, 3)";
        assertResultSequence (xquery, "", "", "");

        // get the first three non-empty titles in order by title, using the default "empty least":
        // this requires iterating over all the blank titles first, and discarding
        xquery = "subsequence(for $doc in collection() order by $doc/lux:field-values('title') " +
        		"return $doc/lux:field-values('title'), 1, 3)";
        assertResultSequence (xquery, "ACT I", "ACT II", "ACT III");

        // get the first three non-empty titles in order by title, using "empty greatest":
        // this requires Saxon to perform the sorting since we don't implement empty greatest using Lucene
        xquery = "subsequence(for $doc in collection() order by $doc/lux:field-values('title') empty greatest " +
                "return string($doc/*/TITLE), 1, 3)";
        assertResultSequence (xquery, "ACT I", "ACT II", "ACT III");
        
        // Get the first three non-empty titles in reverse order by title, using the default "empty least":
        xquery = "subsequence(for $doc in collection() order by $doc/lux:field-values('title') descending " +
        		"return $doc/lux:field-values('title'), 1, 2)";
        assertResultSequence (xquery, "The Tragedy of Hamlet, Prince of Denmark", 
                "SCENE VII.  Another room in the castle.");
    }
    
    @Test
    public void testFieldValuesNoContext () throws Exception {
        try {
            assertResultSequence ("lux:field-values('title')", "");
            assertFalse ("expected exception not thrown", true);
        } catch (Exception e) {
            assertTrue (e.getMessage().contains("there is no context defined"));
        }
        // FIXME: this should return some kind of error?  The order by expression gets 
        // removed by the optimizer, but we should make sure it has a context
        try {
            assertResultSequence  ("for $doc in collection() order by lux:field-values('title') return $doc");
            assertFalse ("expected exception not thrown", true);
        } catch (Exception e) {
            assertTrue (e.getMessage().contains("there is no context defined"));
        }
    }
    
    @Test
    public void testFieldValuesNoField () throws Exception {
        // no error, just return empty sequence
        assertResultSequence ("collection()[1]/lux:field-values('bogus')");
    }
    
    @Test
    public void testAtomizingEmptySequence () throws Exception {
        String query = "subsequence (for $doc in collection() return string ($doc/*/TITLE), 1, 3)";
        // should return titles of the first three nodes in document order
        assertResultSequence (query, "The Tragedy of Hamlet, Prince of Denmark", "", "");
    }
    
    private void assertResultSequence (String query, String ... results) throws TransformerException {
        XdmResultSet resultSet = eval.evaluate(query);
        if (resultSet.getErrors() != null) {
            throw resultSet.getErrors().get(0);
        }
        Iterator<XdmItem> iter = resultSet.iterator();
        for (String result : results) {
            assertTrue ("too few results returned", iter.hasNext());
            assertEquals (result, iter.next().getStringValue());
        }
        assertFalse ("query returned extra results", iter.hasNext());
    }

    // TODO: test empty greatest / empty least
}
