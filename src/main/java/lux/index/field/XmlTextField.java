package lux.index.field;

import java.util.Collections;

import lux.index.XmlIndexer;
import lux.index.analysis.DefaultAnalyzer;
import lux.index.analysis.XmlTextTokenStream;
import lux.xml.SaxonDocBuilder;
import net.sf.saxon.s9api.XdmNode;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Fieldable;

public class XmlTextField extends FieldDefinition {

    private static final XmlTextField instance = new XmlTextField();
    
    public static XmlTextField getInstance() {
        return instance;
    }
    
    protected XmlTextField () {
        super ("lux_text", new DefaultAnalyzer(), Store.NO, Type.TOKENS, true);
    }
    
    @Override
    public Iterable<? extends Fieldable> getFieldValues(XmlIndexer indexer) {
        XdmNode doc = indexer.getXdmNode();
        if (doc != null && doc.getUnderlyingNode() != null) {
            SaxonDocBuilder builder = indexer.getSaxonDocBuilder();
            String fieldName = indexer.getConfiguration().getFieldName(this);
            Analyzer analyzer = getAnalyzer();
            TokenStream textTokens = XmlTextTokenStream.reusableTokenStream(analyzer, fieldName);
            XmlTextTokenStream tokens = new XmlTextTokenStream (fieldName, analyzer, textTokens, doc, builder.getOffsets());
            return new FieldValues (indexer.getConfiguration(), this, Collections.singleton(new Field(indexer.getConfiguration().getFieldName(this), tokens)));
        }
        return Collections.emptySet();
    }

}
