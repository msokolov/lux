package lux.index.field;

import java.util.Collections;

import lux.index.XmlIndexer;
import lux.index.analysis.DefaultAnalyzer;
import lux.index.analysis.XmlTextTokenStream;
import lux.xml.SaxonDocBuilder;
import net.sf.saxon.s9api.XdmNode;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.document.Fieldable;

public class XmlTextField extends FieldDefinition {

    private static final XmlTextField instance = new XmlTextField();
    
    public static XmlTextField getInstance() {
        return instance;
    }
    
    protected XmlTextField () {
        super ("lux_text", new DefaultAnalyzer(), Store.NO, Type.TOKENS, TermVector.NO, true);
    }
    
    @Override
    public Iterable<Fieldable> getFieldValues(XmlIndexer indexer) {
        XdmNode doc = indexer.getXdmNode();
        SaxonDocBuilder builder = indexer.getSaxonDocBuilder();
        XmlTextTokenStream tokens = new XmlTextTokenStream (doc, builder.getOffsets());
        return new FieldValues (indexer.getConfiguration(), this, Collections.singleton(new Field(indexer.getConfiguration().getFieldName(this), tokens, getTermVector())));
    }

}
