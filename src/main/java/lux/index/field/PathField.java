package lux.index.field;

import lux.index.XmlIndexer;
import lux.index.analysis.WhitespaceGapAnalyzer;

import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;

public class PathField extends FieldDefinition {
    
    private static final PathField instance = new PathField();
    
    public static PathField getInstance() {
        return instance;
    }
    
    protected PathField () {
        super ("lux_path", new WhitespaceGapAnalyzer(), Store.NO, Type.STRING, TermVector.NO);
    }
    
    @Override
    public Iterable<?> getValues(XmlIndexer indexer) {
        return indexer.getPathMapper().getPathCounts().keySet();
    }

}
