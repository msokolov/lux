package lux.index;

/**
 * Stores character data, like StringBuilder, but provides equals() that compares contents 
 * so this can be used as a key in a map, where it will compare equals() to any CharSequence
 * containing the same sequence of chars.
 */
public class MutableString implements CharSequence {

    private int length;
    private char[] buffer;
    //private int hash;
    
    public MutableString (int capacity) {
        buffer = new char[capacity];
        length = 0;
        //hash = 0;
    }
    
    public MutableString () {
        this (64);
    }
    
    public MutableString(MutableString o) {
        this (o.length());
        append (o);
    }

    public MutableString(CharSequence o) {
        this (o.length());
        append (o.toString());
    }

    @Override 
    public int hashCode() {
        //int h = hash;
        int h = 0;
        //if (h == 0) {
            for (int i = 0; i < length; i++) {
                h = 31*h + buffer[i];
            }
            // hash = h;
        // }
        return h;
    }
    
    @Override
    public boolean equals (Object other) {
        if (other == null) {
            return false;
        }
        if (other instanceof MutableString) {
            return equals ((MutableString) other);
        }
        if (other instanceof CharSequence) {
            return equals ((CharSequence) other);
        }
        return false;
    }
        
    private boolean equals (CharSequence o) {
        if (o.length() != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            // is there no native method for doing memcmp?
            if (buffer[i] != o.charAt(i)) {
                return false;
            }
        }
        return true;
    }
    
    private boolean equals (MutableString o) {
        if (o.length != length) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            // is there no native method for doing memcmp?
            if (buffer[i] != o.buffer[i]) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public String toString () {
        return new String(buffer, 0, length);
    }
    
    public MutableString append (char c) {
        assureCapacity (1);
        buffer[length++] = c;
        return this;
    }
    
    public MutableString append (String s) {
        int l = s.length();
        assureCapacity (l);
        s.getChars(0, l, buffer, length);
        length += l;
        return this;
    }
    
    public MutableString append(MutableString s) {
        assureCapacity(s.length);
        System.arraycopy(s.buffer, 0, buffer, length, s.length);
        length += s.length;
        return this;
    }
    
    public MutableString append(char[] s) {
        assureCapacity(s.length);
        System.arraycopy(s, 0, buffer, length, s.length);
        length += s.length;
        return this;
    }

    private final void assureCapacity (int n) {
        if (length + n > buffer.length) {
            char[] expanded = new char[buffer.length * 2];
            System.arraycopy (buffer, 0, expanded, 0, buffer.length);
            buffer = expanded;
        }
    }

    @Override
    public char charAt(int index) {
        return buffer[index];
    }

    @Override
    public int length() {
        return length;
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        char[] sub = new char[end-start];
        System.arraycopy(buffer, start, sub, 0, end-start);
        return new String(sub);
    }

    public void setLength(int i) {
        length = i;
    }

}
