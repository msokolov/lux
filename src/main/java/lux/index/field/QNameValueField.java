package lux.index.field;

import java.util.Collections;

import lux.index.XPathValueMapper;
import lux.index.XmlIndexer;

import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.util.Version;

public class QNameValueField extends XmlField {
    
    private static final QNameValueField instance = new QNameValueField();
    
    public static QNameValueField getInstance() {
        return instance;
    }
    
    protected QNameValueField () {
        super ("lux_path", new WhitespaceAnalyzer(Version.LUCENE_34), Store.NO, Type.TOKENS);
    }
    
    @Override
    public Iterable<Fieldable> getFieldValues(XmlIndexer indexer) {
        // replace with a custom Fieldable
        XPathValueMapper mapper = (XPathValueMapper) indexer.getPathMapper();        
        return new FieldValues (this, Collections.singleton
                (new TokenizedField(getName(), 
                        new QNameValueTokenStream
                        (mapper.getPathValues()), Store.NO, Index.ANALYZED, TermVector.NO)));
    }

}
