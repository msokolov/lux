package lux.index.field;

import java.util.Collections;

import lux.index.FieldRole;
import lux.index.XPathValueMapper;
import lux.index.XmlIndexer;
import lux.index.analysis.QNameValueTokenStream;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.Version;

public class QNameValueField extends FieldDefinition {
    
    public QNameValueField () {
        super (FieldRole.QNAME_VALUE, new WhitespaceAnalyzer(Version.LUCENE_44), Store.NO, Type.TOKENS);
    }
    
    @Override
    public Iterable<IndexableField> getFieldValues(XmlIndexer indexer) {
        // replace with a custom Fieldable
        XPathValueMapper mapper = (XPathValueMapper) indexer.getPathMapper();        
        return new FieldValues (this, Collections.singleton
                (new TextField(getName(), new QNameValueTokenStream (mapper.getPathValues ()))));
    }

}
