package lux.index.field;

import java.io.IOException;
import java.util.Collections;

import lux.index.FieldRole;
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

    public XmlTextField () {
        super (FieldRole.XML_TEXT, new DefaultAnalyzer(), Store.NO, Type.TOKENS, true);
    }
    
    public XmlTextField (String name, Analyzer analyzer) {
        super (analyzer, Store.NO, Type.TOKENS);
        setName(name);
    }
    
    @Override
    public Iterable<IndexableField> getFieldValues(XmlIndexer indexer) {
        XdmNode doc = indexer.getXdmNode();
        if (doc != null && doc.getUnderlyingNode() != null) {
            SaxonDocBuilder builder = indexer.getSaxonDocBuilder();
            String fieldName = getName();
            Analyzer analyzer = getAnalyzer();
            TokenStream textTokens=null;
            try {
                textTokens = analyzer.tokenStream(fieldName, new CharSequenceReader(""));
            } catch (IOException e) { }
            XmlTextTokenStream tokens = new XmlTextTokenStream (fieldName, analyzer, textTokens, doc, builder.getOffsets());
            return new FieldValues (this, Collections.singleton(new TextField(fieldName, tokens)));
        }
        return Collections.emptySet();
    }

}
