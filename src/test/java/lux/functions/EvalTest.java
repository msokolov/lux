package lux.functions;

import static org.junit.Assert.*;
import lux.Evaluator;
import lux.XdmResultSet;

import org.junit.Before;
import org.junit.Test;

public class EvalTest {
	
	private Evaluator eval;
	
	@Before public void init () {
		eval = new Evaluator();
	}
	
	@Test
	public void testEval () throws Exception {
		XdmResultSet result = eval.evaluate("lux:eval('1 + 1')");
		assertEquals ("2", result.getXdmValue().getUnderlyingValue().getStringValue());
	}

}
