package lux.index;

import static org.junit.Assert.*;

import org.junit.Test;

public class MutableStringTest {

    @Test
    public void testHashCode () {
        // ensure that hash code is equal to String hashCode
        MutableString s = new MutableString ("test");
        assertEquals (s.hashCode(), "test".hashCode());
        assertEquals (s, "test");
        assertFalse ("test".equals(s));
    }
    
}
