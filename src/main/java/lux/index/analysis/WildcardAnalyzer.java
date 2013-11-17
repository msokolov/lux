package lux.index.analysis;

import java.io.Reader;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;
import org.apache.lucene.util.Version;

/**
 * Used by the XML query parser to handle wildcarded terms.
 * TODO: replace with something more general based on the existing analysis chain
 * like Solr does with MultiTermAware components when we upgrade to 3.6/4.x.  In NodeQueryBuilder,
 * we have the main Analyzer; this should have a ctor: WildcardAnalyzer(Analyzer base) ...
 */
public class WildcardAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName, Reader aReader) {
        Tokenizer tokenizer = new KeywordTokenizer(aReader);
        TokenStream outer = new LowerCaseFilter(Version.LUCENE_44, new ASCIIFoldingFilter(tokenizer));
        return new TokenStreamComponents(tokenizer, outer);
    }

}
