package lux.xml;

import static org.junit.Assert.*;

import org.junit.Test;

public class QNameTest {

	@Test public void testClarkName () {
		QName name = new QName ("test");
		assertEquals ("test", name.getClarkName());
		assertEquals ("{http://test}test", new QName("http://test", "test").getClarkName());
	}
}
