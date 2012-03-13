package lux.xmlbin;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

import java.util.Random;

public class VIntTest {

    @Test public void testVInt () {
        byte[] bin = new byte[5];
        assertVIntEncoding (0, bin);
        assertVIntEncoding (1, bin);
        assertVIntEncoding (127, bin);
        assertVIntEncoding (128, bin);
        assertVIntEncoding (65536, bin);
        assertVIntEncoding (Integer.MAX_VALUE, bin);
        Random random = new Random();
        for (int i = 0; i < 100; i++) {
            int num = random.nextInt();
            assertVIntEncoding (num, bin);
        }
    }

    private void assertVIntEncoding (int num, byte[] bin) {
        int nbytes = VInt.put (num, bin, 0);
        assertEquals (nbytes, VInt.vlength(num));
        if (num >=  0) {
            int decoded = VInt.get (bin, 0);
            assertEquals (num, decoded);
        }
    }
}