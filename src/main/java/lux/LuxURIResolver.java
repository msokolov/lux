package lux;

import java.io.IOException;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;

import lux.exception.NotFoundException;
import lux.search.DocIterator;
import lux.search.LuxSearcher;
import net.sf.saxon.s9api.XdmNode;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TermQuery;

public class LuxURIResolver implements URIResolver {

    private final URIResolver systemURIResolver;
    private final LuxSearcher searcher;
    private final CachingDocReader docReader;
    private final String uriFieldName;
    
    /**
     * @param evaluator
     */
    LuxURIResolver(URIResolver systemURIResolver, LuxSearcher searcher, CachingDocReader docReader, String uriFieldName) {
        this.systemURIResolver = systemURIResolver;
        this.searcher = searcher;
        this.docReader = docReader;
        this.uriFieldName = uriFieldName;
    }

    /**
     * Evaluator provides this method as an implementation of URIResolver so as to resolve uris in service of fn:doc().
     * file: uri resolution is delegated to the default resolver by returning null.  lux: and other uris are all resolved
     * using the provided searcher.  The lux: prefix is optional, e.g: the uris "lux:/hello.xml" and "/hello.xml"
     * are equivalent.  Documents read from the index are numbered according to their Lucene docIDs, and retrieved
     * using the {@link CachingDocReader}.
     * @throws IllegalStateException if a search is attempted, but no searcher was provided
     * @throws TransformerException if the document is not found in the index, or there was an IOException
     * thrown by Lucene.
     */
    @Override
    public Source resolve(String href, String base) throws TransformerException {
        boolean isFile;
        String path = href;
        if (href.matches("^\\w+:.*$")) {
            isFile = href.startsWith("file:");
            if (isFile) {
                path = href.substring(5);
            } else if (href.startsWith("lux:/")) {
                path = href.substring(5);
            }
        } else {
            // relative url, look at base
            if (base != null) {
                isFile = base.startsWith("file:");
            } else {
                isFile = false;
            }
        }
        if (isFile) {
            return systemURIResolver.resolve (path, base);
        }
        if (searcher == null) {
            throw new IllegalStateException ("Attempted search, but no searcher was provided");
        }
        path = path.replace('\\', '/');
        try {
            DocIterator disi = searcher.search(new TermQuery(new Term(uriFieldName, path)));
            int docID = disi.nextDoc();
            if (docID == DocIdSetIterator.NO_MORE_DOCS) {
                throw new NotFoundException(href);
            }
            XdmNode doc = docReader.get(docID, disi.getCurrentReaderContext());
            return doc.asSource(); 
        } catch (IOException e) {
            throw new TransformerException(e);
        }
    }

}