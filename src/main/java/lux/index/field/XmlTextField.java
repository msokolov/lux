package lux.index.field;

import java.io.IOException;
import java.util.Collections;

import lux.index.XmlIndexer;
import lux.index.analysis.DefaultAnalyzer;
import lux.index.analysis.XmlTextTokenStream;
import lux.xml.SaxonDocBuilder;
import net.sf.saxon.s9api.XdmNode;

import org.apache.commons.io.input.CharSequenceReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexableField;

public class XmlTextField extends FieldDefinition {

    private static final XmlTextField instance = new XmlTextField();
    
    public static XmlTextField getInstance() {
        return instance;
    }
    
    protected XmlTextField () {
        super ("lux_text", new DefaultAnalyzer(), Store.NO, Type.TOKENS, true);
    }
    
    @Override
    public Iterable<IndexableField> getFieldValues(XmlIndexer indexer) {
        XdmNode doc = indexer.getXdmNode();
        if (doc != null && doc.getUnderlyingNode() != null) {
            SaxonDocBuilder builder = indexer.getSaxonDocBuilder();
            String fieldName = indexer.getConfiguration().getFieldName(this);
            Analyzer analyzer = getAnalyzer();
            TokenStream textTokens=null;
            try {
                textTokens = analyzer.tokenStream(fieldName, new CharSequenceReader(""));
            } catch (IOException e) { }
            XmlTextTokenStream tokens = new XmlTextTokenStream (fieldName, analyzer, textTokens, doc, builder.getOffsets());
            return new FieldValues (indexer.getConfiguration(), this, Collections.singleton(new TextField(indexer.getConfiguration().getFieldName(this), tokens)));
        }
        return Collections.emptySet();
    }

}
