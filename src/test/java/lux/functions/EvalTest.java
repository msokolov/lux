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
		assertEquals ("2", result.getXdmValue().itemAt(0).getStringValue());
	}

	@Test
	public void testEvalArgBinding () throws Exception {
		QueryContext context = new QueryContext();
		context.bindVariable(new QName("x"), "2");
		context.bindVariable(new QName("y"), 3);
		context.setContextItem(null);
		context.bindVariable(new QName("x"), null);
		XdmResultSet result = eval.evaluate("declare variable $y external; lux:eval('declare variable $x external; $x', ('x', $y))", context);
		if (! result.getErrors().isEmpty()) {
			fail (result.getErrors().get(0).toString());
		}
		assertEquals ("3", result.getXdmValue().itemAt(0).getStringValue());
	}
	
	@Test
	public void testParamNS () throws Exception {
		XdmResultSet result = eval.evaluate("lux:eval('declare variable $lux:x external; $lux:x', ('lux:x', 3))");
		if (! result.getErrors().isEmpty()) {
			fail (result.getErrors().get(0).toString());
		}
		assertEquals ("3", result.getXdmValue().itemAt(0).getStringValue());
	}

	@Test
	public void testOddParam () throws Exception {
		XdmResultSet result = eval.evaluate("lux:eval('2', ('lux:x'))");
		assertTrue (! result.getErrors().isEmpty());
		assertEquals ("Odd number of items in third argument to lux:transform, which should be parameter/value pairs", result.getErrors().get(0).getMessage());
	}

	@Test
	public void testEvalRuntimeError () throws Exception {
		XdmResultSet result = eval.evaluate("lux:eval('1 div (fn:seconds-from-dateTime(current-dateTime()) - fn:seconds-from-dateTime(current-dateTime()))')");
		assertFalse (result.getErrors().isEmpty());
		assertEquals ("Decimal divide by zero", result.getErrors().get(0).getMessage());
	}

	@Test
	public void testEvalCompileError () throws Exception {
		XdmResultSet result = eval.evaluate("lux:eval('1 +')");
		assertFalse (result.getErrors().isEmpty());
		assertEquals ("Unexpected token \"<eof>\" in path expression", result.getErrors().get(0).getMessage());
	}

}
