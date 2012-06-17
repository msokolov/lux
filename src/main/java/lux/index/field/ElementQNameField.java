package lux.index.field;

import lux.index.XmlIndexer;

import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Fieldable;

public final class ElementQNameField extends XmlField {

    private static final ElementQNameField instance = new ElementQNameField();
    
    public static final ElementQNameField getInstance() {
        return instance;
    }
    
    protected ElementQNameField () {
        super ("lux_elt_name", new KeywordAnalyzer(), Store.NO, Type.STRING);
    }
    
    @Override
    public Iterable<Fieldable> getFieldValues(XmlIndexer indexer) {
        return new FieldValues (this, indexer.getPathMapper().getEltQNameCounts().keySet());
    }
    
    @Override
    public Iterable<?> getValues(XmlIndexer indexer) {
        return indexer.getPathMapper().getEltQNameCounts().keySet();
    }

}
