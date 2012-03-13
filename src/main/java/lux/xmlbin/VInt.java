package lux.xmlbin;

/**
 * This is essentially a base 128 encoding where the high bit of each byte
 * indicates whether the VInt extends to another digit.

 *
 * 1 byte: 0-127
 *
 * 2 bytes: 128-16383
 *
 * 3 bytes: 16384-2097151
 *
 * etc.

 */
public final class VInt {

    /**
     * Get the integer value stored in the buffer as a VInt at the
     * indicated position.
     * @param bin the buffer containing the VInt
     * @param offset the starting position of the VInt 
     * @return the encoded int value
     */
    public static final int get (byte[] bin, int offset) {
        int val = 0;
        val += bin[offset] & 0x7f;
        if (hasNextByte(bin, offset)) {
            val |= ((bin[++offset] & 0x7f) << 7);
            if (hasNextByte(bin, offset)) {
                val |= ((bin[++offset] & 0x7f) << 14);
                if (hasNextByte(bin, offset)) {
                    val |= ((bin[++offset] & 0x7f) << 21);
                    if (hasNextByte(bin, offset)) {
                        val |= ((bin[++offset] & 0x7f) << 28);
                    }
                }
            }
        }
        return val;
    }

    /**
     * @param bin
     * @param offset
     * @return
     */
    private static final boolean hasNextByte(byte[] bin, int offset) {
        return (bin[offset] & 0x80) != 0;
    }

    /**
     * Store the positive value in the buffer starting at the position indicated by
     * offset.  Negative values are not stored.
     @param value the value to store
     @param bin the buffer in which to store the VInt
     @param offset the position in the buffer at which to store the VInt
     @return the number of bytes required to store the value (1-5), or 0 if the value is negative
     */
    public static final int put (int value, byte[] bin, int offset) {
        if (value < 0) {
            return 0;
        }
        int count = 0;
        do {
            byte b = (byte) (value & 0x7f);
            if ((value &  ~0x7f) != 0) {
                b |= 0x80;
            }
            bin[offset + count++] = b;
            value >>= 7;
        } while (value > 0);
        return count;
    }

    /**
     * @return the number of bytes required to encode the value.
     */
    public static final int vlength (int value) {
        if (value < 0) return 0;
        if (value < 128) return 1;
        if (value < 127 * (1 + 128)) return 2;
        if (value < 127 * (1 + 128 + 128*128)) return 3;
        if (value < 127 * (1 + 128 + 128*128 + 128*128*128)) return 4;
        return 5;
    }

}