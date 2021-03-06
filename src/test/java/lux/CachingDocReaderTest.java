package lux;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Arrays;

import lux.index.analysis.DefaultAnalyzer;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;

import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class CachingDocReaderTest {
	
	private Evaluator eval;
	
	@Before
	public void init () throws IOException {
		eval = Evaluator.createEvaluator(new RAMDirectory());
	}
	
	/**
	 * Once we implement storing binary documents, we can test retrieving them!
	 * @throws Exception
	 */
	@Test @Ignore
	public void testBinaryDocument() throws Exception {
		String insertQuery = 
				" let $test := text { '' } " +
				" return (lux:delete('lux:/'), lux:insert('/minus.xqy', $test), lux:commit())";
		XdmResultSet result = eval.evaluate(insertQuery);
		assertSuccessfulResult(result);
		eval.reopenSearcher(); // see result of insert
		XdmNode doc = eval.getDocReader().get(0, eval.getSearcher().getIndexReader());
		Object data = ((TinyDocumentImpl)doc.getUnderlyingNode()).getUserData("_binaryDocument");
		assertTrue ("unexpected data type", data instanceof byte[]);
		byte[] bytes = (byte[]) data;
		String q = new String (bytes, "utf-8");
		assertEquals (insertQuery, q);
	}

    private void assertSuccessfulResult(XdmResultSet result) {
        if (! result.getErrors().isEmpty()) {
			result.getErrors().get(0).printStackTrace();
			fail(result.getErrors().get(0).toString());
		}
    }

    private void assertResult(String s, XdmResultSet result) {
        assertSuccessfulResult (result);
        XdmSequenceIterator iter = result.getXdmValue().iterator();
        assertEquals (s, iter.next().getStringValue());
        assertFalse ("more than one result", iter.hasNext());
    }
	
	/**
	 * Test retrieving a document that has no lux fields
	 * @throws Exception
	 */
	@Test
	public void testEmptyDocument () throws Exception {
	    RAMDirectory dir = new RAMDirectory();
	    // add a document to the index that has no xml-valued field:
	    IndexWriter writer = new IndexWriter (dir, new IndexWriterConfig(Version.LUCENE_46, new DefaultAnalyzer()));
	    StringField field = new StringField("string", "value", Store.YES);
	    writer.addDocument(Arrays.asList(new StringField[] { field } ));
	    writer.commit();
	    writer.close();
	    Evaluator e = Evaluator.createEvaluator (dir);
	    
	    XdmNode doc = e.getDocReader().get(0, e.getSearcher().getIndexReader());
	    assertNotNull (doc);
	    assertEquals ("", doc.getStringValue());
	    assertEquals (XdmNodeKind.DOCUMENT, doc.getNodeKind());
	    
	    try {
	        doc = e.getDocReader().get(1, e.getSearcher().getIndexReader());
	        fail ("expected exception not thrown");
	    } catch (IllegalArgumentException e1) {
	    }
	}
	
	@Test
	public void testOverwriteDocument () throws Exception {
        XdmResultSet result = eval.evaluate("lux:insert('/test', <test>1</test>)");
        assertSuccessfulResult(result); 
        result = eval.evaluate("lux:insert('/test', <test2>2</test2>)");
        assertSuccessfulResult(result);
        eval.reopenSearcher();
        assertResult ("1", eval.evaluate("count(doc('/test'))"));
        assertResult ("2", eval.evaluate("doc('/test')/test2"));
	}
}
