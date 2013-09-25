package lux;

import java.io.IOException;

import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;

import lux.exception.NotFoundException;
import lux.search.DocIterator;
import lux.search.LuxSearcher;
import net.sf.saxon.s9api.XdmNode;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.TermQuery;

public class LuxURIResolver extends BaseURIResolver {

    private final LuxSearcher searcher;
    private final CachingDocReader docReader;
    private final String uriFieldName;
    
    /**
     * @param evaluator
     */
    LuxURIResolver(URIResolver systemURIResolver, LuxSearcher searcher, CachingDocReader docReader, String uriFieldName) {
        super (systemURIResolver);
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
    public XdmNode getDocument (String path) throws TransformerException {
        if (searcher == null) {
            throw new IllegalStateException ("Attempted search, but no searcher was provided");
        }
        try {
            DocIterator disi = searcher.search(new TermQuery(new Term(uriFieldName, path)));
            int docID = disi.nextDoc();
            if (docID == DocIdSetIterator.NO_MORE_DOCS) {
                throw new NotFoundException(path);
            }
            return docReader.get(docID, disi.getCurrentReaderContext());
        } catch (IOException e) {
            throw new TransformerException(e);
        }
    }

}