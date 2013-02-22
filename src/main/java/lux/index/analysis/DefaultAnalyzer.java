package lux.index.analysis;

import java.io.Reader;

import lux.index.IndexConfiguration;

import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.ReusableAnalyzerBase;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;

public final class DefaultAnalyzer extends ReusableAnalyzerBase {

    @Override
    protected TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer tokenizer = new StandardTokenizer(IndexConfiguration.LUCENE_VERSION, reader);
        TokenStream tokenStream =  new LowerCaseFilter(IndexConfiguration.LUCENE_VERSION, tokenizer);
        // ASCIIFoldingFilter
        // Stemming
        return new TokenStreamComponents(tokenizer, tokenStream);
    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
