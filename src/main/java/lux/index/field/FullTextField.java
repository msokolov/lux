package lux.index.field;

import java.util.Iterator;

import lux.index.XmlIndexer;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.util.Version;
import org.jdom.filter.ContentFilter;

/**
 * TODO: This is not currently in use; we will want to replace the use of JDOM with XdmNode anyway I think
 *
 */
public class FullTextField extends XmlField {
    
    private static final FullTextField instance = new FullTextField();
    
    public static FullTextField getInstance() {
        return instance;
    }
    
    protected FullTextField () {
        super ("lux_text", new StandardAnalyzer(Version.LUCENE_34), Store.YES, Type.STRING);
    }
    
    @Override
    public Iterable<Fieldable> getFieldValues(XmlIndexer indexer) {
        @SuppressWarnings("unchecked")
        final Iterator<Object> textIter = indexer.getJDOM().getDescendants (new ContentFilter(ContentFilter.TEXT | ContentFilter.CDATA));
        return new FieldValues (this, new Iterable<Object> () {
            public Iterator<Object> iterator() {
                return textIter;
            }
        });
    }

}
