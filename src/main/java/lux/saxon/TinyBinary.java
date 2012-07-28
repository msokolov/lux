package lux.saxon;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.NamePool;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.tree.tiny.AppendableCharSequence;
import net.sf.saxon.tree.tiny.CharSlice;
import net.sf.saxon.tree.tiny.LargeStringBuffer;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;
import net.sf.saxon.tree.tiny.TinyTree;
import net.sf.saxon.tree.util.FastStringBuffer;

// TODO: baseURI
// TODO: UTF-8
public class TinyBinary {

    /* TinyDocumentImpl:
     * 
    private String baseURI;
    
    TinyBinary layout:
    4 bytes int charBufferSize; // size, in chars, of char buffer
    4 bytes int commentBufferSize; // size, in chars, of comment buffer
    4 bytes int numberOfNodes;
    4 bytes int numberOfAttributes;
    4 bytes int numberOfNamespaceDecls;
    4 bytes int numberOfNames;
    4 bytes int numberOfNamespaces;
    4 bytes int numberOfAttValues;
    1 * numberOfNodes bytes byte[] nodeKind;
    2 * numberOfNodes bytes short[] depth;
    4 * numberOfNodes bytes int[] next;
    4 * numberOfNodes bytes int[] alpha;
    4 * numberOfNodes bytes int[] beta;
    4 * numberOfNodes bytes int[] nameCode;
    4 * numberOfAttributes bytes int[] attParent;
    4 * numberOfAttributes bytes int[] attNameCode;
    4 * numberOfNamespaces bytes int[] namespaceParent;
    4 * numberOfNamespaces bytes int[] namespaces;
    names - numberOfNames strings (16 bit count followed by count 16-bit chars)
    namespaces - number of namespaces strings
    attValues - number of (distinct) attValues strings
     */
    
    private ByteBuffer bytes;
    private int charBufferLength;
    private int commentBufferLength;
    private int nodeCount;  // number of (non-attribute) nodes
    private int attCount;   // numbner of attributes
    private int nsCount;    // number of namespace declarations
    private int nameCount; // number of element/attribute names
    private int nsNameCount; // number of distinct prefixes and namespaces
    private int attValueCount;  // number of distinct attribute values  
    
    private LinkedHashMap<CharSequence, Integer> namespaces;
    private LinkedHashMap<CharSequence, Integer> names;
    private LinkedHashMap<CharSequence, Integer> attValues;
    private HashMap<Integer, Integer> nameCodeMap;
    private CharsetDecoder charsetDecoder;
    private CharsetEncoder charsetEncoder;
    
    public TinyBinary (byte[] buf) {
        this (buf, null);
    }
    
    public TinyBinary (byte[] buf, Charset charset) {
        this.bytes = ByteBuffer.wrap(buf);
        charBufferLength = bytes.getInt();
        commentBufferLength = bytes.getInt();
        nodeCount = bytes.getInt();
        attCount = bytes.getInt();
        nsCount = bytes.getInt();
        nameCount = bytes.getInt();
        nsNameCount = bytes.getInt();
        attValueCount = bytes.getInt();
        if (charset != null) {
            this.charsetDecoder = charset.newDecoder();
        }
    }
    
    public TinyDocumentImpl getTinyDocument (Configuration config) {
        
        // Allocate TinyTree storage
        byte[] nodeKind = new byte[nodeCount];
        short[] depth = new short[nodeCount];
        int[] next = new int[nodeCount];
        int[] alpha = new int[nodeCount];
        int[] beta = new int[nodeCount];
        int[] nameCode = new int[nodeCount];
        int[] attParent = new int[attCount];
        int[] attNameCode = new int[attCount];
        int[] nsParent = new int[nsCount];
        int[] nsNameCode = new int[nsCount];
        NamespaceBinding[] binding = new NamespaceBinding[nsCount];
        AppendableCharSequence charBuffer;
        if (charBufferLength > 65000) {
            charBuffer = new LargeStringBuffer();
        } else {
            charBuffer = new FastStringBuffer(charBufferLength);            
        }
        FastStringBuffer commentBuffer = null;
        if (commentBufferLength > 0) {
            commentBuffer = new FastStringBuffer(commentBufferLength);
        }
        
        // fill the buffers
        IntBuffer ints = bytes.asIntBuffer();
        ints.get(next);
        ints.get(alpha);
        ints.get(beta);
        ints.get(nameCode);
        ints.get(attParent);
        ints.get(attNameCode);
        ints.get(nsParent);
        ints.get(nsNameCode);

        bytes.position(bytes.position() + ints.position() * 4);
        // System.out.println ("position after reading all int buffers: " + bytes.position());
        ShortBuffer shorts = bytes.asShortBuffer();
        shorts.get(depth);
        bytes.position(bytes.position() + shorts.position() * 2);

        // TODO: evaluate storing as UTF-8 and converting
        getChars(charBuffer, charBufferLength);
        if (commentBufferLength > 0) {
            getChars(commentBuffer, commentBufferLength);
        }
        
        // System.out.println ("position after reading char buffers: " + bytes.position());
        
        String [] nameTable = readStrings (nameCount);
        //System.out.println ("position after reading names: " + bytes.position());
        String [] nsTable = readStrings (nsNameCount);
        //System.out.println ("position after reading namespaces: " + bytes.position());
        CharSequence[] attValue = readCharSequences (attValueCount);
        //System.out.println ("position after reading attribute values:" + bytes.position());

        NamePool namePool = config.getNamePool();
        // TODO: Would it be faster to save the list of distinct nameCodes 
        // for efficiently recreating the pool rather than attempting to allocate every node's name?
        allocateNames(nameCode, nameTable, nsTable, namePool);
        allocateNames(attNameCode, nameTable, nsTable, namePool);
        for (int i = 0; i < nsCount; i++) {
            int nsCode = nsNameCode[i];
            int prefixCode = ((nsCode >> 16) & 0xffff) - 1;
            String prefix = prefixCode < 0 ? "" : nsTable[prefixCode];
            int uriCode = (nsCode & 0xffff) - 1;
            binding[i] = new NamespaceBinding(prefix, nsTable[uriCode]);
        }
        bytes.get(nodeKind);
        TinyTree tree = new TinyTree (config, 
                nodeCount, nodeKind, depth, next, alpha, beta, nameCode, 
                attCount, attParent, attNameCode, attValue,
                nsCount, nsParent, binding, charBuffer, commentBuffer
                );
        return (TinyDocumentImpl) tree.getNode(0);
    }

    /**
     * decode character from the bytes ByteBuffer into the given Character storage.
     * @param charBuffer the character storage
     * @param len the number of characters to decode
     */
    private void getChars(AppendableCharSequence charBuffer, int len) {
        if (charsetDecoder == null) {
            CharBuffer chars = bytes.asCharBuffer();
            chars.limit(len);
            if (charBuffer instanceof LargeStringBuffer) {
                // TODO: don't copy all these chars - implement LargeStringBuffer.append(CharBuffer)
                charBuffer.append(chars.toString());
            } else {
                charBuffer.append(chars);
            }
            bytes.position(bytes.position() + len * 2);
        } else {
            if (charBuffer instanceof FastStringBuffer) {
                FastStringBuffer buffer = ((FastStringBuffer)charBuffer);
                CharBuffer chars = CharBuffer.wrap (buffer.getCharArray(), 0, len);
                charsetDecoder.decode(bytes, chars, false);
                buffer.setLength(len);
            } else {
                CharBuffer chars = CharBuffer.wrap(new char[len]);
                charsetDecoder.decode(bytes, chars, false);
                charBuffer.append(new CharSlice(chars.array()));
            }
        }
    }

    private void allocateNames(int[] nameCode, String[] names, String[] namespaces, NamePool namePool) {
        for (int i = 0; i < nameCode.length; i++) {
            int code = nameCode[i];
            if (code < 0) {
                continue;
            }
            int localNameCode = (code & 0xffff) - 1;
            String localName = localNameCode < 0 ? "" : names[localNameCode];
            int prefixCode = ((code >> 24) & 0xff) - 1;
            String prefix = prefixCode < 0 ? "" : namespaces[prefixCode];
            int uriCode = ((code >> 16) & 0xff) - 1;
            String uri = uriCode < 0 ? "" : namespaces[uriCode];
            //System.out.println ("allocateName " + i + " " + prefix + ":" + localName);
            int poolCode = namePool.allocate(prefix, uri, localName);
            nameCode[i] = poolCode;
        }
    }
    
    private CharSequence[] readCharSequences (final int count) {
        CharSequence[] csqs = new CharSequence[count];
        for (int i = 0; i < count; i++) {
            short len = bytes.getShort();
            CharBuffer chars = bytes.asCharBuffer();
            chars.limit(len);
            csqs[i] = chars;
            bytes.position(bytes.position() + len * 2);
        }
        return csqs;
    }

    private String[] readStrings (final int count) {
        String[] strings = new String[count];
        for (int i = 0; i < count; i++) {
            short len = bytes.getShort();
            CharBuffer chars = bytes.asCharBuffer();
            chars.limit(len);
            strings[i] = chars.toString();
            bytes.position(bytes.position() + len * 2);
        }
        return strings;
    }
 
    public TinyBinary (TinyTree tree) {
        this (tree, null);
    }
    
    public TinyBinary (TinyTree tree, Charset charset) {
        names = new LinkedHashMap<CharSequence, Integer>();
        namespaces = new LinkedHashMap<CharSequence, Integer>();
        attValues = new LinkedHashMap<CharSequence, Integer>();
        nameCodeMap = new HashMap<Integer, Integer>();
        if (charset != null) {
            charsetEncoder = charset.newEncoder();
        }
        int totalSize = calculateTotalSize (tree);
        bytes = ByteBuffer.allocate(totalSize);
        
        // 8 * 4 = 32
        bytes.putInt(tree.getCharacterBuffer().length());
        if (tree.getCommentBuffer() == null)
            bytes.putInt(0);
        else
            bytes.putInt(tree.getCommentBuffer().length());
        bytes.putInt(nodeCount);
        bytes.putInt(attCount);
        bytes.putInt(nsCount);
        bytes.putInt(names.size());
        bytes.putInt(namespaces.size());
        bytes.putInt(attValues.size());

        // 4 * 4 * nodeCount
        IntBuffer ints = bytes.asIntBuffer();
        // put next[]
        ints.put(tree.getNextPointerArray(), 0, nodeCount);
        // put alpha[]
        ints.put(tree.getAlphaArray(), 0, nodeCount);
        // put beta[]
        ints.put(tree.getBetaArray(), 0, nodeCount);
        
        // put name codes, translating via the map
        putMappedNameCodes(tree.getNameCodeArray(), nodeCount, tree, ints);
        
        // 2 * 4 * attCount
        // put att parents
        ints.put(tree.getAttributeParentArray(), 0, attCount);
        // put mapped att name codes
        putMappedNameCodes(tree.getAttributeNameCodeArray(), attCount, tree, ints);
        
        // 2 * 4 * nsCount
        // put ns decl parents
        ints.put(tree.getNamespaceParentArray(), 0, nsCount);
        // put ns decl string references
        NamespaceBinding [] bindings = tree.getNamespaceCodeArray();
        for (int i = 0; i < nsCount; i++) {
            NamespaceBinding binding = bindings[i];
            int a = binding.getPrefix().length() == 0 ? 0 : namespaces.get(binding.getPrefix());
            int b = namespaces.get(binding.getURI());
            ints.put((a << 16) | b);
        }
        
        bytes.position(bytes.position() + ints.position() * 4);

        // System.out.println ("position after writing all int buffers: " + bytes.position());
        
        ShortBuffer shorts = bytes.asShortBuffer();
        
        // 2 * nodeCount
        // put depth[]
        shorts.put(tree.getNodeDepthArray(), 0, nodeCount);

        // put character buffer
        bytes.position(bytes.position() + shorts.position() * 2);

        putCharacterBuffer(tree.getCharacterBuffer(), charsetEncoder);
        if (tree.getCommentBuffer() != null) {
            putCharacterBuffer(tree.getCommentBuffer(), charsetEncoder);        
        }
        // System.out.println ("position after writing char buffers: " + bytes.position());
        
        putStrings(names);
        // System.out.println ("position after writing names: " + bytes.position());
        putStrings(namespaces);
        // System.out.println ("position after writing namespaces: " + bytes.position());
        putStrings(attValues);
        // System.out.println ("position after writing attribute values:" + bytes.position());

        // 1 * nodeCount
        bytes.put(tree.nodeKind, 0, nodeCount);
    }
    
    private void putCharacterBuffer (CharSequence characterBuffer, CharsetEncoder charsetEncoder)
    {
        if (charsetEncoder == null) {
            CharBuffer chars = bytes.asCharBuffer();
            chars.put(characterBuffer.toString());
            bytes.position(bytes.position() + chars.position() * 2);            
        } else if (characterBuffer instanceof FastStringBuffer) {
            CharBuffer chars = CharBuffer.wrap(((FastStringBuffer) characterBuffer).getCharArray(), 0, characterBuffer.length());
            charsetEncoder.encode(chars, bytes, false);
        } else {
            CharBuffer chars = CharBuffer.wrap(characterBuffer, 0, characterBuffer.length());
            charsetEncoder.encode(chars, bytes, false);
        }
    }

    private void putMappedNameCodes(int[] nameCodes, int count, TinyTree tree, IntBuffer ints) {
        for (int i = 0; i < count; i++) {
            int nameCode = nameCodes[i];
            if (nameCode >= 0) {
                ints.put (nameCodeMap.get (nameCode));
            } else {
                ints.put (nameCode);
            }
        }
    }

    private void putStrings(LinkedHashMap<CharSequence, Integer> map) {
        CharBuffer chars = bytes.asCharBuffer();
        for (CharSequence name : map.keySet()) {
            int len = name.length();
            bytes.putShort((short) len);
            chars.position(chars.position() + 1);
            chars.put(name.toString());
            bytes.position(bytes.position() + len * 2);
        }
    }

    private int calculateTotalSize(TinyTree tree) {
        nodeCount = tree.getNumberOfNodes();
        attCount = tree.getNumberOfAttributes();
        nsCount = tree.getNumberOfNamespaces();
        getStrings(tree);
        int stringLen = 12; // 3 * 4 ints = lengths of the string arrays
        for (CharSequence s : names.keySet()) {
            stringLen += s.length() * 2;
            stringLen += 2;
        }
        for (CharSequence s : namespaces.keySet()) {
            stringLen += s.length() * 2;
            stringLen += 2;
        }
        for (CharSequence s : attValues.keySet()) {
            stringLen += s.length() * 2;
            stringLen += 2;
        }
        // FIXME: fudge factor - we were 4 bytes short, but why?
        int alignmentPadding = 4;
        /*
        if (nodeCount % 4 != 0) {
            // padding for the nodeKind byte array
            alignmentPadding += (4 - (nodeCount % 4));
            if (nodeCount % 2 == 1) {
                // padding for the depth short array
                alignmentPadding += 2;
            }
        }
        */
        return 32 + nodeCount * 19 + attCount * 8 + nsCount * 8 +
            tree.getCharacterBuffer().length() * 2 +
            (tree.getCommentBuffer() == null ? 0 : tree.getCommentBuffer().length() * 2) +
            stringLen + 
            alignmentPadding;
    }

    private void getStrings(TinyTree tree) {
        NamePool namePool = tree.getNamePool();
        for (int i = 0; i < nodeCount; i++) {
            int nameCode = tree.getNameCode(i);
            internNameCodeStrings (nameCode, namePool);
        }
        int [] attNameCode = tree.getAttributeNameCodeArray();
        for (int i = 0; i < attCount; i++) {
            int nameCode = attNameCode[i];
            internNameCodeStrings (nameCode, namePool);
        }
        NamespaceBinding[] bindings = tree.getNamespaceCodeArray();
        for (int i = 0; i < nsCount; i++) {
            putString (namespaces, bindings[i].getPrefix());
            putString (namespaces, bindings[i].getURI());
        }
        CharSequence [] attValueArray = tree.getAttributeValueArray();
        for (int i = 0; i < attCount; i++) {
            putString (attValues, attValueArray[i]);
        }
    }

    private void internNameCodeStrings (int nameCode, NamePool namePool) {
        if (nameCode >= 0 && ! nameCodeMap.containsKey (nameCode)) {
            int a = putString (names, namePool.getLocalName(nameCode));
            int b = putString (namespaces, namePool.getPrefix(nameCode));
            int c = putString (namespaces, namePool.getURI(nameCode));
            nameCodeMap.put (nameCode, a | (b << 24) | (c << 16));
        }
    }

    /*
     * Intern char sequence values in a symbol table.  The symbols are indexed, starting at 1.  The index
     * value of 0 indicates a null value.
     */
    private int putString (HashMap<CharSequence,Integer> symbolTable, CharSequence s) {
        if (s == null || s.length() == 0) {
            return 0;
        }
        Integer n = symbolTable.get (s);
        if (n == null) {
            n = symbolTable.size() + 1;
            symbolTable.put (s, n);
        }
        return n;
    }
    
    public byte[] getBytes () {
        return bytes.array();
    }
    
    public int length() {
        return bytes.position();
    }
    
}
