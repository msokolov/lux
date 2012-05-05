package lux.index;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.analysis.TokenStream;

/**
 * pretty much a clone of SolrIndexAnalyzer, but without the rest of solr.
 * If no analyzer is registered for a field name, a default analyzer is returned.  The
 * default analyzer is teh analyzer mapped to the null key.  By default, null is mapped to
 * an instance of KeywordAnalyzer (an analyzer that returns a single token for the entire field value).
 */
public final class MultiFieldAnalyzer extends Analyzer {
    
    private HashMap<String,Analyzer> analyzers;
    
    public MultiFieldAnalyzer () {
        analyzers = new HashMap<String,Analyzer>();
        analyzers.put(null, new KeywordAnalyzer());
    }
    
    public void put (String fieldName, Analyzer analyzer) {
        analyzers.put(fieldName, analyzer);
    }

    protected Analyzer getAnalyzer(String fieldName)
    {
        Analyzer analyzer = analyzers.get(fieldName);
        if (analyzer == null) {
            return analyzers.get(null);            
        }
        return analyzer;
    }

    @Override
    public TokenStream tokenStream(String fieldName, Reader reader)
    {
      return getAnalyzer(fieldName).tokenStream(fieldName,reader);
    }

    @Override
    public TokenStream reusableTokenStream(String fieldName, Reader reader) throws IOException {
      return getAnalyzer(fieldName).reusableTokenStream(fieldName,reader);
    }

    @Override
    public int getPositionIncrementGap(String fieldName) {
      return getAnalyzer(fieldName).getPositionIncrementGap(fieldName);
    }

}
