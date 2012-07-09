package lux.index.field;

import java.util.Collections;

import lux.index.XmlIndexer;
import lux.index.analysis.QNameAnalyzer;
import lux.index.analysis.XmlTextTokenStream;
import lux.query.ParseableQuery;
import lux.query.QNameTextQuery;
import lux.xml.SaxonDocBuilder;
import net.sf.saxon.s9api.XdmNode;

import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.Term;

public class XmlTextField extends XmlField {

    private static final XmlTextField instance = new XmlTextField();
    
    public static XmlTextField getInstance() {
        return instance;
    }
    
    protected XmlTextField () {
        // TODO - better default analyzer w/stemming + diacritic normalization
        // TODO - enable caller to supply analyzer (extending our analyzer so we can ensure that
        // element/attribute text tokens are generated)
        // TODO - provide QName lists as attributes so that analysis doesn't see them embedded in
        // the token text
        super ("lux_text", new QNameAnalyzer(), Store.NO, Type.TOKENS);
    }

    public ParseableQuery makeTextQuery (String value) {
        return new QNameTextQuery(new Term(getName(), value));
    }
    
    @Override
    public Iterable<Fieldable> getFieldValues(XmlIndexer indexer) {
        XdmNode doc = indexer.getXdmNode();
        SaxonDocBuilder builder = indexer.getSaxonDocBuilder();
        XmlTextTokenStream tokens = new XmlTextTokenStream (doc, builder.getOffsets());
        return new FieldValues (this, Collections.singleton(new TokenizedField(getName(), tokens, Store.NO, Index.ANALYZED, TermVector.NO)));
    }

}
