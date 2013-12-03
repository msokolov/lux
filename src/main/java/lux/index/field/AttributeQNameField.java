package lux.index.field;

import lux.index.FieldRole;
import lux.index.XmlIndexer;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Field.Store;

public final class AttributeQNameField extends FieldDefinition {

    public AttributeQNameField () {
        super (FieldRole.ATT_QNAME, new KeywordAnalyzer(), Store.NO, Type.STRING);
    }
    
    @Override
    public Iterable<?> getValues(XmlIndexer indexer) {
        return indexer.getPathMapper().getAttQNameCounts().keySet();
    }
}
