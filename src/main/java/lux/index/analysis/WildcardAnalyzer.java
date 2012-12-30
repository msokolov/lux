package lux.index.analysis;

import java.io.Reader;

import org.apache.lucene.analysis.ASCIIFoldingFilter;
import org.apache.lucene.analysis.KeywordTokenizer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.ReusableAnalyzerBase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.util.Version;

/**
 * Used by the XML query parser to handle wildcarded terms.
 * TODO: replace with something more general based on the existing analysis chain
 * like Solr does with MultiTermAware components when we upgrade to 3.6/4.x
 */
public class WildcardAnalyzer extends ReusableAnalyzerBase {

    @Override
    protected TokenStreamComponents createComponents(String fieldName, Reader aReader) {
        Tokenizer tokenizer = new KeywordTokenizer(aReader);
        TokenStream outer = new LowerCaseFilter(Version.LUCENE_34, new ASCIIFoldingFilter(tokenizer));
        return new TokenStreamComponents(tokenizer, outer);
    }

}
