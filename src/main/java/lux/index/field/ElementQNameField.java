package lux.index.field;

import lux.index.XmlIndexer;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Field.Store;

public final class ElementQNameField extends FieldDefinition {

    private static final ElementQNameField instance = new ElementQNameField();
    
    public static final ElementQNameField getInstance() {
        return instance;
    }
    
    protected ElementQNameField () {
        super ("lux_elt_name", new KeywordAnalyzer(), Store.NO, Type.STRING);
    }
    
    @Override
    public Iterable<?> getValues(XmlIndexer indexer) {
        return indexer.getPathMapper().getEltQNameCounts().keySet();
    }

}
