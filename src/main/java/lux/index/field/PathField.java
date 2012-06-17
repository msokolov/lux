package lux.index.field;

import lux.index.WhitespaceGapAnalyzer;
import lux.index.XmlIndexer;

import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Fieldable;

public class PathField extends XmlField {
    
    private static final PathField instance = new PathField();
    
    public static PathField getInstance() {
        return instance;
    }
    
    protected PathField () {
        super ("lux_path", new WhitespaceGapAnalyzer(), Store.NO, Type.STRING);
    }
    
    @Override
    public Iterable<Fieldable> getFieldValues(XmlIndexer indexer) {
        return new FieldValues (this, indexer.getPathMapper().getPathCounts().keySet());
    }

    @Override
    public Iterable<?> getValues(XmlIndexer indexer) {
        return indexer.getPathMapper().getPathCounts().keySet();
    }

}
