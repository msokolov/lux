package lux.xpath;

import static org.junit.Assert.*;

import java.math.BigDecimal;
import java.util.Date;

import org.junit.Test;

import lux.exception.LuxException;
import lux.xml.QName;
import lux.xml.ValueType;

public class TestLiteral {

	@Test
	public void testValueType () {
		assertEquals ("xs:integer", new LiteralExpression(new Integer(1)).getValueType().name);
		assertEquals ("xs:integer", new LiteralExpression(new Long(1)).getValueType().name);
		assertEquals ("xs:float", new LiteralExpression(new Float(1)).getValueType().name);
		assertEquals ("xs:double", new LiteralExpression(new Double(1)).getValueType().name);
		assertEquals ("xs:decimal", new LiteralExpression(new BigDecimal(1)).getValueType().name);
		assertEquals ("xs:boolean", new LiteralExpression(new Boolean(true)).getValueType().name);
		assertEquals ("xs:string", new LiteralExpression("1").getValueType().name);
		assertEquals ("xs:QName", new LiteralExpression(new QName("x")).getValueType().name);
	}
	
	@Test
	public void testToString() {
		assertEquals ("()", new LiteralExpression(null).toString());
		assertEquals ("1", new LiteralExpression(1).toString());
		assertEquals ("1", new LiteralExpression(1L).toString());
		assertEquals ("xs:float(1.0)", new LiteralExpression(1.0f).toString());
		assertEquals ("xs:float('-INF')", new LiteralExpression(-1/0f).toString());
		assertEquals ("xs:float('INF')", new LiteralExpression(1/0f).toString());
		assertEquals ("xs:float('NaN')", new LiteralExpression(0/0f).toString());
		assertEquals ("xs:double(1.0)", new LiteralExpression((double) 1.0).toString());
		assertEquals ("xs:double('-INF')", new LiteralExpression(-1/0.0).toString());
		assertEquals ("xs:double('INF')", new LiteralExpression(1/0.0).toString());
		assertEquals ("xs:double('NaN')", new LiteralExpression(0/0.0).toString());
		assertEquals ("xs:decimal(1)", new LiteralExpression(new BigDecimal(1)).toString());
		assertEquals ("fn:true()", new LiteralExpression(true).toString());
		assertEquals ("\"1\"", new LiteralExpression("1").toString());
		assertEquals ("fn:QName(\"http://www.w3.org/2005/xpath-functions\",\"fn:data\")", new LiteralExpression(FunCall.FN_DATA).toString());
		assertEquals ("xs:untypedAtomic(\"1\")", new LiteralExpression (1, ValueType.UNTYPED_ATOMIC).toString());
		assertEquals ("1", new LiteralExpression (1, ValueType.ATOMIC).toString());
		try {
			new LiteralExpression (new Date());
		} catch (LuxException e) {
			assertEquals ("unsupported java object type: Date", e.getMessage());
		}
	}
	
	@Test
	public void testToStringBinary() {
		assertEquals ("xs:hexBinary(\"30310F00\")", new LiteralExpression(new byte[] {48, 49, 15, 0}, ValueType.HEX_BINARY).toString());
		assertEquals ("xs:base64Binary(\"MDEPAA==\")", new LiteralExpression(new byte[] {48, 49, 15, 0}, ValueType.BASE64_BINARY).toString());
	}
	
	@Test
	public void testJavaEquals () {
		// equals and hash code ignore the declared type and are based on the underlying java value
		assertTrue (! LiteralExpression.ONE.equals(new LiteralExpression (1L, ValueType.ATOMIC)));
		assertTrue (LiteralExpression.ONE.hashCode() != new LiteralExpression (1L, ValueType.ATOMIC).hashCode());
	}

}
