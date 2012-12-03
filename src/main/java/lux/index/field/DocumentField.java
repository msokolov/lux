package lux.index.field;

import java.util.Collections;

import lux.index.XmlIndexer;

import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;

/**
 * A stored field that is used to store the entire XML document.
 *
 */
public class DocumentField extends FieldDefinition {
    
    private static final DocumentField instance = new DocumentField();
    
    public static DocumentField getInstance() {
        return instance;
    }
    
    protected DocumentField () {
        super ("lux_xml", null, Store.YES, Type.STRING, TermVector.NO);
    }
    
    @Override
    public Iterable<?> getValues(XmlIndexer indexer) {
        return Collections.singleton(indexer.getDocumentText());
    }

}
