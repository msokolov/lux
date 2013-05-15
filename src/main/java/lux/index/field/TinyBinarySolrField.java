package lux.index.field;

import java.util.Collections;

import lux.index.XmlIndexer;
import lux.xml.tinybin.TinyBinary;

public class TinyBinarySolrField extends TinyBinaryField {
    
    private static final TinyBinarySolrField instance = new TinyBinarySolrField();
    
    public static TinyBinarySolrField getInstance() {
        return instance;
    }
    
    @Override
    public Iterable<?> getValues(XmlIndexer indexer) {
        TinyBinary tinyBinary = makeTinyBinary(indexer);
        byte[] bytes = tinyBinary.getBytes();
        if (bytes.length > tinyBinary.length()) {
            // copy the bytes - SolrJ doesn't have a way to deal with a reference
            // into a byte array (TODO: see about overriding SolrInputField??)
            bytes = new byte[tinyBinary.length()];
            System.arraycopy(tinyBinary.getBytes(), 0, bytes, 0, tinyBinary.length());
        }
        return Collections.singleton(bytes);
    }
}
