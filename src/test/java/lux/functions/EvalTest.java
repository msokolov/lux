package lux.functions;

import static org.junit.Assert.*;
import lux.Evaluator;
import lux.QueryContext;
import lux.XdmResultSet;
import lux.xml.QName;

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

	@Test
	public void testEvalArgBinding () throws Exception {
		QueryContext context = new QueryContext();
		context.bindVariable(new QName("x"), "2");
		context.bindVariable(new QName("y"), 3);
		XdmResultSet result = eval.evaluate("declare variable $y external; lux:eval('declare variable $x external; $x', ('x', $y))", context);
		if (! result.getErrors().isEmpty()) {
			fail (result.getErrors().get(0).toString());
		}
		assertEquals ("3", result.getXdmValue().getUnderlyingValue().getStringValue());
	}

	@Test
	public void testEvalRuntimeError () throws Exception {
		XdmResultSet result = eval.evaluate("lux:eval('1 div 0')");
		assertFalse (result.getErrors().isEmpty());
		assertEquals ("Integer division by zero", result.getErrors().get(0).getMessage());
	}

	@Test
	public void testEvalCompileError () throws Exception {
		XdmResultSet result = eval.evaluate("lux:eval('1 +')");
		assertFalse (result.getErrors().isEmpty());
		assertEquals ("Unexpected token \"<eof>\" in path expression", result.getErrors().get(0).getMessage());
	}

}
