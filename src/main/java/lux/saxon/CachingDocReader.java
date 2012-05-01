package lux.saxon;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;

import lux.SingleFieldSelector;
import lux.saxon.Saxon.SaxonBuilder;
import net.sf.saxon.s9api.XdmNode;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;

/**
 * Keeps an association from docID->document.  This should be dropped when the reader is.
 * Later on, we might want a longer-lived cache, but for now this is really only useful
 * for a single query.  There is no expiration - once a document is cached, it is retained for 
 * the lifetime of the cache. This is needed to ensure document identity is preserved within the
 * context of a query.
 *
 */
public class CachingDocReader {
    private final HashMap <Integer, XdmNode> cache = new HashMap<Integer, XdmNode>();
    private final String xmlFieldName;
    private final SingleFieldSelector fieldSelector;
    private final IndexReader reader;
    private final SaxonBuilder builder;
    private int cacheHits=0;
    private int cacheMisses=0;
    
    public CachingDocReader (IndexReader reader, SaxonBuilder builder, String xmlFieldName) {
        this.reader = reader;
        this.builder = builder;
        this.xmlFieldName = xmlFieldName;
        fieldSelector = new SingleFieldSelector(xmlFieldName);
    }
    
    public XdmNode get (int docID) throws IOException {
        if (cache.containsKey(docID)) {
            ++cacheHits;
            return cache.get(docID);
        }
        Document document;
        document = reader.document(docID, fieldSelector);
        String xml = document.get(xmlFieldName);
        int n = xml.indexOf('\n');
        n = (n < 0 || n > 30) ? Math.min(30,xml.length()) : n-1;
        //System.out.println ("GET " + docID + " " + xml.substring(0, n));
        XdmNode node = (XdmNode) builder.build(new StringReader (xml), docID);
        if (node != null) {
            cache.put(docID, node);
        }
        ++cacheMisses;
        return node;
    }
    
    public final boolean isCached (final int docID) {
        return cache.containsKey(docID);
    }

    public int getCacheHits() {
        return cacheHits;
    }

    public int getCacheMisses() {
        return cacheMisses;
    }

    public void clear() {
        cache.clear();
        cacheHits = 0;
        cacheMisses = 0;
    }

}
