package lux.index.field;

import java.util.Collections;

import lux.index.XmlIndexer;

import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Fieldable;

public class DocumentField extends XmlField {
    
    private static final DocumentField instance = new DocumentField();
    
    public static DocumentField getInstance() {
        return instance;
    }
    
    protected DocumentField () {
        super ("lux_xml", null, Store.YES, Type.STRING);
    }
    
    @Override
    public Iterable<Fieldable> getFieldValues(XmlIndexer indexer) {
        String doc = indexer.getDocumentText();
        if (doc == null) {
            return new FieldValues (this, Collections.emptySet());
        }
        return new FieldValues (this, Collections.singleton(doc));
    }

}
