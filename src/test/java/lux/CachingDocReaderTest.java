package lux;

import static org.junit.Assert.*;

import java.io.IOException;

import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;

import org.apache.lucene.store.RAMDirectory;
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
	@Test @Ignore("unimplemented")
	public void testBinaryDocument() throws Exception {
		String insertQuery = "let $xquery := doc('file:src/test/resources/lux/compiler/minus.xqy') " +
				" let $test := '' " +
				" return (lux:insert('/minus.xqy', $xquery), lux:commit())";
		XdmResultSet result = eval.evaluate(insertQuery);
		if (! result.getErrors().isEmpty()) {
			result.getErrors().get(0).printStackTrace();
			fail(result.getErrors().get(0).toString());
		}
		XdmNode doc = eval.getDocReader().get(0, eval.getSearcher().getIndexReader());
		Object data = ((TinyDocumentImpl)doc.getUnderlyingNode()).getUserData("_binaryDocument");
		assertTrue ("unexpected data type", data instanceof byte[]);
		byte[] bytes = (byte[]) data;
		String q = new String (bytes, "utf-8");
		assertEquals (insertQuery, q);
	}
}
