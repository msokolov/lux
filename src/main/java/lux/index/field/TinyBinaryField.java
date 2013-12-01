package lux.index.field;

import java.nio.charset.Charset;
import java.util.Collections;

import lux.index.FieldRole;
import lux.index.XmlIndexer;
import lux.xml.tinybin.TinyBinary;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;
import net.sf.saxon.tree.tiny.TinyTree;

import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StoredField;

/**
 * A field that stores XML documents in a binary form ({@link TinyBinary}) that is very close to the in-memory Saxon TinyTree format.
 * Reading and writing these documents avoids the cost of parsing and serialization, and they take up only
 * slightly more space than the serialized XML form.
 */
public class TinyBinaryField extends FieldDefinition {
	public static final Charset UTF8 = Charset.forName("utf-8");

    public TinyBinaryField () {
        super (FieldRole.XML_STORE, null, Store.YES, Type.BYTES, true);
    }
    
    protected TinyBinary makeTinyBinary (XmlIndexer indexer) {
        TinyTree tinyTree = ((TinyDocumentImpl) indexer.getXdmNode().getUnderlyingNode()).getTree();
        return new TinyBinary (tinyTree, UTF8);
    }
    
    @Override
    public Iterable<?> getValues(XmlIndexer indexer) {
        TinyBinary tinyBinary = makeTinyBinary(indexer);
        return Collections.singleton(new StoredField(getName(), tinyBinary.getBytes(), 0, tinyBinary.length()));
    }

}
