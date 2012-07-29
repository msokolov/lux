package lux.saxon;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
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
import net.sf.saxon.type.Type;

import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.ByteArrayDataOutput;

// TODO: baseURI
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
    private int attCount;   // number of attributes
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
    
    // To read a TinyTree
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
        // System.out.println ("nodeCount: " + nodeCount);
        
        bytes.get(nodeKind);

        int pos = bytes.position();
        // fill the buffers
        // IntBuffer ints = bytes.asIntBuffer();
        // ints.get(next);
        readVInts(bytes, next, nodeCount);
        // System.out.println ("next[]: " + (bytes.position() - pos));
        pos = bytes.position();
        // ints.get(alpha);
        // readVInts(bytes, alpha, nodeCount);
        readAlpha (bytes, alpha, nodeKind, nodeCount);
        // System.out.println ("alpha[]: " + (bytes.position() - pos));
        pos = bytes.position();
        
        // ints.get(beta);
        // readVInts (bytes, beta, nodeCount);
        readBeta (bytes, beta, nodeKind, nodeCount);
        // System.out.println ("beta[]: " + (bytes.position() - pos));
        pos = bytes.position();
        // System.out.println ("pos=" + pos);
        
        readMappedNameCodes(nameCode, nodeCount, bytes);
//        IntBuffer ints = bytes.asIntBuffer();
//        ints.get(nameCode);
//        bytes.position(bytes.position() + ints.position() * 4);
        // System.out.println ("nameCode[]: " + (bytes.position() - pos));
        pos = bytes.position();

        // ints.get(attParent);
        readDeltas (bytes, attParent, attCount);
        
        readMappedNameCodes(attNameCode, attCount, bytes);
//        ints = bytes.asIntBuffer();
//        ints.get(attNameCode);
//        // ints.get(nsParent);
//        bytes.position(bytes.position() + ints.position() * 4);
        
        readDeltas (bytes, nsParent, nsCount);
        readMappedNameCodes(nsNameCode, nsCount, bytes);
//        ints = bytes.asIntBuffer();
//        ints.get(nsNameCode, 0, nsCount);
//        bytes.position(bytes.position() + ints.position() * 4);
        
        pos = bytes.position();
        
        // System.out.println ("position after reading all int buffers: " + bytes.position());
        // ShortBuffer shorts = bytes.asShortBuffer();
        // shorts.get(depth);
        // bytes.position(bytes.position() + shorts.position() * 2);
        readShortDeltas (bytes, depth, nodeCount);
        
        // System.out.println ("depth[]: " + (bytes.position() - pos));
        pos = bytes.position();

        getChars(charBuffer, charBufferLength);
        if (commentBufferLength > 0) {
            getChars(commentBuffer, commentBufferLength);
        }
        // System.out.println ("char(and comment)Buffer[]: " + (bytes.position() - pos));
        pos = bytes.position();
        
        // System.out.println ("position after reading char buffers: " + bytes.position());
        
        String [] nameTable = readStrings (nameCount, charsetDecoder);
        //System.out.println ("position after reading names: " + bytes.position());
        String [] nsTable = readStrings (nsNameCount, charsetDecoder);
        //System.out.println ("position after reading namespaces: " + bytes.position());
        CharSequence[] attValue = readCharSequences (attValueCount, charsetDecoder);
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
        // System.out.println ("names[]: " + (bytes.position() - pos));
        // System.out.println ("total: " + bytes.position());

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
            // System.out.println ("allocateName " + i + " " + prefix + ":" + localName);
            int poolCode = namePool.allocate(prefix, uri, localName);
            nameCode[i] = poolCode;
        }
    }
    
    private CharSequence[] readCharSequences (final int count, CharsetDecoder decoder) {
        CharSequence[] csqs = new CharSequence[count];
        for (int i = 0; i < count; i++) {
            short len = bytes.getShort();
            CharBuffer chars;
            if (decoder == null) {
                chars = bytes.asCharBuffer();
                chars.limit(len);
                bytes.position(bytes.position() + len * 2);
            } else {
                chars = CharBuffer.wrap(new char[len]);
                decoder.decode(bytes, chars, false);
                chars.position(0);
            }
            csqs[i] = chars;
        }
        return csqs;
    }

    private String[] readStrings (final int count, CharsetDecoder decoder) {
        String[] strings = new String[count];
        for (int i = 0; i < count; i++) {
            short len = bytes.getShort();
            CharBuffer chars;
            if (decoder == null) {
                chars = bytes.asCharBuffer();
                chars.limit(len);
                bytes.position(bytes.position() + len * 2);
                strings[i] = chars.toString();
            } else {
                // TODO: re-use this char[]
                chars = CharBuffer.wrap(new char[len]);
                decoder.decode(bytes, chars, false);
                strings[i] = new String (chars.array(), 0, chars.position());
            }
        }
        return strings;
    }
 
    // To write a TinyTree

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
        //System.out.println ("computed total size: " + totalSize + ", nodeCount=" + nodeCount);
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
        
        //System.out.println ("position after writing counts: " + bytes.position());

        // 1 * nodeCount
        bytes.put(tree.nodeKind, 0, nodeCount);

        // 4 * 4 * nodeCount
        IntBuffer ints = bytes.asIntBuffer();
        // put next[]
        // ints.put(tree.getNextPointerArray(), 0, nodeCount);
        // Could have a large positive jump if this element contains a lot of descendants
        // Could also have a large negative jump (last sibling in a large element?)
        // but should tend to be increasing
        writeVInts (bytes, tree.getNextPointerArray(), nodeCount);
        //System.out.println ("position after writing next[]: " + bytes.position());

        // put alpha[]
        // ints.put(tree.getAlphaArray(), 0, nodeCount);
        // these are increasing sequences for each of elements, text, and comments/pis
        // writeVInts (bytes, tree.getAlphaArray(), nodeCount);
        writeAlpha (bytes, tree.getAlphaArray(), tree.nodeKind, nodeCount);
        //System.out.println ("position after writing alpha[]: " + bytes.position());
        
        // put beta[]
        // ints.put(tree.getBetaArray(), 0, nodeCount);
        // these are lengths, except for elements they are namespace indexes
        // writeVInts (bytes, tree.getBetaArray(), nodeCount);
        writeBeta(bytes, tree.getBetaArray(), tree.nodeKind, nodeCount);
        // System.out.println ("position after writing beta[]: " + bytes.position());

        // put name codes, translating via the map
        // name codes are essentially random largish integers, although docs w/no namespaces
        // always have namecodes that fit in 16 bits
        writeMappedNameCodes(tree.getNameCodeArray(), nodeCount, bytes);
        
        // 2 * 4 * attCount
        // put att parents
        // ints.put(tree.getAttributeParentArray(), 0, attCount);
        // this should be an nondecreasing sequence?
        writeDeltas (bytes, tree.getAttributeParentArray(), attCount);

        // put mapped att name codes
        // ints = bytes.asIntBuffer();
        // bytes.position(bytes.position() + ints.position() * 4);
        writeMappedNameCodes(tree.getAttributeNameCodeArray(), attCount, bytes);
        
        // 2 * 4 * nsCount
        // put ns decl parents - TODO encode as byte/short for smaller documents
        // ints.put(tree.getNamespaceParentArray(), 0, nsCount);
        writeDeltas (bytes, tree.getNamespaceParentArray(), nsCount);

        // put ns decl string references
        // ints = bytes.asIntBuffer();
        ByteArrayDataOutput out = new ByteArrayDataOutput(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
        NamespaceBinding [] bindings = tree.getNamespaceCodeArray();
        try {
            for (int i = 0; i < nsCount; i++) {
                NamespaceBinding binding = bindings[i];
                int a = binding.getPrefix().length() == 0 ? 0 : namespaces.get(binding.getPrefix());
                int b = namespaces.get(binding.getURI());
                // ints.put((a << 16) | b);
                out.writeVInt((a << 16) | b);
            }
        } catch (IOException e) {}
        bytes.position(out.getPosition() - bytes.arrayOffset());
        
        // bytes.position(bytes.position() + ints.position() * 4);
        // System.out.println ("position after writing all int buffers: " + bytes.position());
        
        // ShortBuffer shorts = bytes.asShortBuffer();
        
        // 2 * nodeCount
        // put depth[]
        // shorts.put(tree.getNodeDepthArray(), 0, nodeCount);
        writeShortDeltas (bytes, tree.getNodeDepthArray(), nodeCount);

        // put character buffer
        // bytes.position(bytes.position() + shorts.position() * 2);

        putCharacterBuffer(tree.getCharacterBuffer(), charsetEncoder);
        if (tree.getCommentBuffer() != null) {
            putCharacterBuffer(tree.getCommentBuffer(), charsetEncoder);        
        }
        // System.out.println ("position after writing char buffers: " + bytes.position());
        
        writeStrings(names, charsetEncoder);
        //System.out.println ("position after writing names: " + bytes.position());
        writeStrings(namespaces, charsetEncoder);
        //System.out.println ("position after writing namespaces: " + bytes.position());
        writeStrings(attValues, charsetEncoder);
        //System.out.println ("position after writing attribute values:" + bytes.position());

    }
    
    private void writeAlpha (ByteBuffer bytes, int[] alpha, byte[] nodeKind, int count)
    {
        // the alpha array stores offset into the character buffer for text, offset into the
        // comment buffer for comments and pis, and index of the first attribute for elements (or -1)
        int textOffset=0, commentOffset=0, attrIndex = -1;
        ByteArrayDataOutput out = new ByteArrayDataOutput(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
        try {
            int k;
            for (int i = 0; i < count; i++) {
                switch (nodeKind[i]) {
                case Type.TEXT:
                    k = alpha[i] - textOffset;
                    textOffset = alpha[i];
                    break;
                case Type.ELEMENT:
                    if (alpha[i] < 0) {
                        k = 0;
                    } else {
                        k = alpha[i] - attrIndex;
                        attrIndex = alpha[i];
                    }
                    break;
                case Type.COMMENT:
                case Type.PROCESSING_INSTRUCTION:
                    k = alpha[i] - commentOffset;
                    commentOffset = alpha[i];
                    break;
                case Type.WHITESPACE_TEXT:
                    out.writeInt(alpha[i]);
                    continue;
                case Type.PARENT_POINTER:
                case Type.STOPPER:
                case Type.DOCUMENT:
                    k = alpha[i];
                    break;
                default:
                    throw new IllegalStateException("unexpected node kind: " + nodeKind[i]);
                }
                out.writeVInt(k);
            }
        } catch (IOException e) { }
        bytes.position(out.getPosition() - bytes.arrayOffset());
    }

    private void readAlpha (ByteBuffer bytes, int[] alpha, byte[] nodeKind, int count)
    {
        int textOffset=0, commentOffset=0, attrIndex = -1;
        ByteArrayDataInput in = new ByteArrayDataInput(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
        for (int i = 0; i < count; i++) {
            int k;
            switch (nodeKind[i]) {
            case Type.TEXT:
                k = in.readVInt();
                textOffset += k;
                alpha[i] = textOffset;
                break;
            case Type.ELEMENT:
                k = in.readVInt();
                if (k == 0) {
                    alpha[i] = -1;
                } else {
                    attrIndex += k;
                    alpha[i] = attrIndex;
                }
                break;
            case Type.COMMENT:
            case Type.PROCESSING_INSTRUCTION:
                k = in.readVInt();
                commentOffset += k;
                alpha[i] = commentOffset;
                break;
            case Type.WHITESPACE_TEXT:
                alpha[i] = in.readInt();
                break;
            case Type.PARENT_POINTER:
            case Type.STOPPER:
            case Type.DOCUMENT:
                alpha[i] = in.readVInt();
                break;
            default:
                throw new IllegalStateException("unexpected node kind: " + nodeKind[i]);
            }
        }
        bytes.position(in.getPosition() - bytes.arrayOffset());
    }

    private void writeBeta(ByteBuffer bytes, int[] beta, byte[] nodeKind, int count)
    {
        // the beta array stores length of text, comment and pi nodes,
        // and index of the first namespace decl for elements (or -1)
        int nsIndex = -1;
        ByteArrayDataOutput out = new ByteArrayDataOutput(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
        try {
            int k;
            for (int i = 0; i < count; i++) {
                switch (nodeKind[i]) {
                case Type.ELEMENT:
                    if (beta[i] < 0) {
                        k = 0;
                    } else {
                        k = beta[i] - nsIndex;
                        nsIndex = beta[i];
                    }
                    out.writeVInt(k);
                    break;
                case Type.PARENT_POINTER:
                case Type.STOPPER:
                case Type.DOCUMENT:
                case Type.TEXT:
                case Type.COMMENT:
                case Type.PROCESSING_INSTRUCTION:
                case Type.WHITESPACE_TEXT:
                    out.writeVInt(beta[i]);
                    break;
                default:
                    throw new IllegalStateException("unexpected node kind: " + nodeKind[i]);
                }
            }
        } catch (IOException e) { }
        bytes.position(out.getPosition() - bytes.arrayOffset());
    }

    private void readBeta (ByteBuffer bytes, int[] beta, byte[] nodeKind, int count)
    {
        int attrIndex = -1;
        ByteArrayDataInput in = new ByteArrayDataInput(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
        for (int i = 0; i < count; i++) {
            switch (nodeKind[i]) {
            case Type.ELEMENT:
                attrIndex += in.readVInt();
                beta[i] = attrIndex;
                break;
            case Type.PARENT_POINTER:
            case Type.STOPPER:
            case Type.DOCUMENT:
            case Type.TEXT:
            case Type.COMMENT:
            case Type.PROCESSING_INSTRUCTION:
            case Type.WHITESPACE_TEXT:
                beta[i] = in.readVInt();
                break;
            default:
                throw new IllegalStateException("unexpected node kind: " + nodeKind[i]);
            }
        }
        bytes.position(in.getPosition() - bytes.arrayOffset());
    }
    
    private void writeVInts (ByteBuffer bytes, int[] ints, int count)
    {
        ByteArrayDataOutput out = new ByteArrayDataOutput(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
        try {
            for (int i = 0; i < count; i++) {
                out.writeVInt(ints[i]);
            }
        } catch (IOException e) { }
        bytes.position(out.getPosition() - bytes.arrayOffset());
    }

    private void readVInts (ByteBuffer bytes, int[] ints, int count)
    {
        ByteArrayDataInput in = new ByteArrayDataInput(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
        for (int i = 0; i < count; i++) {
            ints[i] = in.readVInt();
        }
        bytes.position(in.getPosition() - bytes.arrayOffset());
    }
    
    private void readDeltas (ByteBuffer bytes, int[] ints, int count)
    {
        int k = 0;
        for (int i = 0; i < count; i++) {
            k = k + bytes.get();
            ints[i] = k;
        }
        // System.out.println ("after readDeltas " + count + " pos=" + bytes.position());
    }

    private void readShortDeltas (ByteBuffer bytes, short[] shorts, int count)
    {
        short k = 0;
        for (int i = 0; i < count; i++) {
            k = (short) (k + bytes.get());
            shorts[i] = k;
        }
    }

    private void writeDeltas (ByteBuffer bytes, int[] ints, int count)
    {
        int k = 0;
        for (int i = 0; i < count; i++) {
            k = ints[i] - k;
            assert (k <= 127 && k >= -128);
            bytes.put ((byte) (k & 0xff));
            k = ints[i];
        }
        //System.out.println ("after writeDeltas " + count + " pos=" + bytes.position());
    }

    private void writeShortDeltas (ByteBuffer bytes, short[] shorts, int count)
    {
        int k = 0;
        for (int i = 0; i < count; i++) {
            k = shorts[i] - k;
            assert (k <= 127 && k >= -128);
            bytes.put ((byte) (k & 0xff));
            k = shorts[i];
        }
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

    private void writeMappedNameCodes(int[] nameCodes, int count, ByteBuffer bytes) {
        ByteArrayDataOutput out = new ByteArrayDataOutput(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
        try {
            for (int i = 0; i < count; i++) {
                int nameCode = nameCodes[i];
                if (nameCode >= 0) {
                    out.writeVInt(nameCodeMap.get (nameCode));
                } else {
                    // out.writeByte (0)?
                    out.writeVInt(0);
                }
            }
        } catch (IOException e) { }
        bytes.position(out.getPosition() - bytes.arrayOffset());
    }

    private void readMappedNameCodes(int[] nameCodes, int count, ByteBuffer bytes) {
        ByteArrayDataInput in = new ByteArrayDataInput(bytes.array(), bytes.arrayOffset() + bytes.position(), bytes.remaining());
        for (int i = 0; i < count; i++) {
            int nameCode = in.readVInt();
            if (nameCode > 0) {
                nameCodes[i] = nameCode;
            } else {
                nameCodes[i] = -1;
            }
        }
        bytes.position(in.getPosition() - bytes.arrayOffset());
    }

    private void writeStrings(LinkedHashMap<CharSequence, Integer> map, CharsetEncoder charsetEncoder) {
        CharBuffer chars = bytes.asCharBuffer();
        for (CharSequence name : map.keySet()) {
            int len = name.length();
            bytes.putShort((short) len);
            if (charsetEncoder == null) {
                chars.position(chars.position() + 1);
                chars.put(name.toString());
                bytes.position(bytes.position() + len * 2);
            } else {
                chars = CharBuffer.wrap(name);
                charsetEncoder.encode(chars, bytes, false);                
            }
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
