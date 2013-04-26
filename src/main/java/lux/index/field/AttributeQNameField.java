package lux.index.field;

import lux.index.XmlIndexer;

import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.document.Field.Store;

public final class AttributeQNameField extends FieldDefinition {

    private static final AttributeQNameField instance = new AttributeQNameField();
    
    public static final AttributeQNameField getInstance() {
        return instance;
    }
    
    protected AttributeQNameField () {
        super ("lux_att_name", new KeywordAnalyzer(), Store.NO, Type.STRING);
    }
    
    @Override
    public Iterable<?> getValues(XmlIndexer indexer) {
        return indexer.getPathMapper().getAttQNameCounts().keySet();
    }
}
