package lux.xmlbin;

/*
 * These VInts are not a great idea here since you have to parse them
 * sequentially in order to unravel this structure.  A better compromise
 * would be a bunch of fixed-width ints where the width can vary by
 * document.  The document header would include a symbol table (for names)
 * and an offset size that is used throughout.  That way small documents can
 * have smaller offsets and save a little space.
 * 
 * Node layout (not all nodes have all of these):
 *
 * type (byte)
 * 
 * length (int)
 * 
 * parent-offset (int)
 * 
 * first-child-offset (int)
 * 
 * preceding-sibling-offset (int)
 *
 * preceding-offset (int)
 *
 * following-offset (int) 
 *
 * name (string)
 *
 * namespace (string; optional)
 *
 * value (string)
 *
 * attribute count (byte)
 *
 * child count (byte) ?
 *
 * child nodes (node*)
 */
public class Node {
    protected byte[] bin;
    
    public static final byte DOCUMENT = 1;
    public static final byte ELEMENT = 2;
    public static final byte TEXT = 3;
    public static final byte ATTRIBUTE = 4;
    public static final byte COMMENT = 5;
    public static final byte PROCESSING_INSTRUCTION = 6;
    public static final byte NAMESPACE = 7;

    // TODO - other xdm types? sequence? string etc?

    public final byte getType () {
        return bin[0];
    }

    public final int getLength () {
        return VInt.get(bin, 1);
    }

    // TODO move to utility class
    public static String getStringValue (byte[] bin, int offset) {
        int len = VInt.get(bin, offset);
        return new String (bin, offset + VInt.vlength(len), len);
    }

}
