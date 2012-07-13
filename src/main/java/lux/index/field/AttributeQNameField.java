package lux.index.field;

import lux.index.XmlIndexer;

import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;

public final class AttributeQNameField extends XmlField {

    private static final AttributeQNameField instance = new AttributeQNameField();
    
    public static final AttributeQNameField getInstance() {
        return instance;
    }
    
    protected AttributeQNameField () {
        super ("lux_att_name", new KeywordAnalyzer(), Store.NO, Type.STRING, TermVector.NO);
    }
    
    @Override
    public Iterable<Field> getFieldValues(XmlIndexer indexer) {
        return new FieldValues (this, indexer.getPathMapper().getAttQNameCounts().keySet());
    }
    
    @Override
    public Iterable<?> getValues(XmlIndexer indexer) {
        return indexer.getPathMapper().getAttQNameCounts().keySet();
    }
}
