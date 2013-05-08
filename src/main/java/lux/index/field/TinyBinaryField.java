package lux.index.field;

import java.nio.charset.Charset;
import java.util.Collections;

import lux.index.XmlIndexer;
import lux.xml.tinybin.TinyBinary;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;
import net.sf.saxon.tree.tiny.TinyTree;

import org.apache.lucene.document.Field.Store;

/**
 * A field that stores XML documents in a binary form ({@link TinyBinary}) that is very close to the in-memory Saxon TinyTree format.
 * Reading and writing these documents avoids the cost of parsing and serialization, and they take up only
 * slightly more space than the serialized XML form.
 */
public class TinyBinaryField extends FieldDefinition {
	public static final Charset UTF8 = Charset.forName("utf-8");

    private static final TinyBinaryField instance = new TinyBinaryField();
    
    public static TinyBinaryField getInstance() {
        return instance;
    }
    
    protected TinyBinaryField () {
        super ("lux_xml", null, Store.YES, Type.BYTES);
    }
    
    @Override
    public Iterable<?> getValues(XmlIndexer indexer) {
        TinyTree tinyTree = ((TinyDocumentImpl) indexer.getXdmNode().getUnderlyingNode()).getTree();
        TinyBinary tinyBinary = new TinyBinary (tinyTree, UTF8);
        return Collections.singleton(tinyBinary);
    }

}
