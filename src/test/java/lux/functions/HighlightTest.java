package lux.functions;

import static org.junit.Assert.*;
import lux.Evaluator;
import lux.SearchTest;
import lux.XdmResultSet;
import net.sf.saxon.s9api.XdmEmptySequence;

import org.junit.Before;
import org.junit.Test;

/**
 * A few edge cases are tested here.  There are a bunch of tests of lux:highlight in
 * {@link SearchTest}
 */
public class HighlightTest {
private Evaluator eval;
	
	@Before public void init () {
		eval = new Evaluator();
	}
	
	@Test
	public void testHighlightEmpty() throws Exception {
		XdmResultSet result = eval.evaluate("lux:highlight('term', ())");
		assertEquals(XdmEmptySequence.getInstance(), result.getXdmValue());
		assertTrue(result.getErrors().isEmpty());
	}

	@Test
	public void testHighlightParseError() throws Exception {
		XdmResultSet result = eval.evaluate("lux:highlight('term AND', <foo />)");
		assertTrue(! result.getErrors().isEmpty());
		result = eval.evaluate("lux:highlight(<TermQuery />, <foo />)");
		assertTrue(! result.getErrors().isEmpty());
	}
	
}
