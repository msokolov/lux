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
		XdmResultSet result = eval.evaluate("lux:highlight((), 'term')");
		assertEquals(XdmEmptySequence.getInstance(), result.getXdmValue());
		assertTrue(result.getErrors().isEmpty());
	}

	@Test
	public void testHighlightParseError() throws Exception {
		XdmResultSet result = eval.evaluate("lux:highlight(<foo/>, 'term AND')");
		assertTrue(! result.getErrors().isEmpty());
		result = eval.evaluate("lux:highlight(<foo />, <TermQuery />)");
		assertTrue(! result.getErrors().isEmpty());
	}
	
	@Test
	public void testHighlight() throws Exception {
		XdmResultSet result = eval.evaluate("lux:highlight(<a>there is a term here</a>, 'term')");
		assertEquals("<a>there is a <B>term</B> here</a>", result.getXdmValue().toString().trim());
		assertTrue(result.getErrors().isEmpty());
	}
	
	@Test
	public void testHighlightStringTagName() throws Exception {
		XdmResultSet result = eval.evaluate("lux:highlight(<a>there is a term here</a>, 'term', 'hi')");
		assertEquals("<a>there is a <hi>term</hi> here</a>", result.getXdmValue().toString().trim());
		assertTrue(result.getErrors().isEmpty());
	}

	@Test
	public void testHighlightQName() throws Exception {
		XdmResultSet result = eval.evaluate("lux:highlight(<a>there is a term here</a>, 'term', xs:QName('hi'))");
		assertEquals("<a>there is a <hi>term</hi> here</a>", result.getXdmValue().toString().trim());
		assertTrue(result.getErrors().isEmpty());
	}

	@Test
	public void testHighlightQNameNS() throws Exception {
		XdmResultSet result = eval.evaluate("lux:highlight(<a>there is a term here</a>, 'term', QName('#ns', 'hi'))");
		// Saxon insists on generating a random namespace prefix here, so we don't bother testing that
		// assertEquals("<a>there is a <hi xmlns='#ns'>term</hi> here</a>", result.getXdmValue().toString().trim());
		assertTrue(result.getErrors().isEmpty());
	}

	@Test
	public void testHighlightBadTagName() throws Exception {
		XdmResultSet result = eval.evaluate("lux:highlight(<a>there is a term here</a>, 'term', <hi />)");
		assertEquals(1, result.getErrors().size());
		assertEquals(XdmEmptySequence.getInstance(), result.getXdmValue());
	}

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
