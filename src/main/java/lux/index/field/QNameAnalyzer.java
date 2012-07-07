package lux.index.field;

import java.io.Reader;

import lux.index.XmlIndexer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardTokenizer;

public final class QNameAnalyzer extends Analyzer {

    // TODO wrap an existing Analyzer
    
    @Override
    public TokenStream tokenStream(String fieldName, Reader reader) {
        return new LowerCaseFilter(XmlIndexer.LUCENE_VERSION, new StandardTokenizer(XmlIndexer.LUCENE_VERSION, reader));
    }

}