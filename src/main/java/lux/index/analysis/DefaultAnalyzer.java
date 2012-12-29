package lux.index.analysis;

import java.io.Reader;

import lux.index.IndexConfiguration;

import org.apache.lucene.analysis.ASCIIFoldingFilter;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardTokenizer;

public final class DefaultAnalyzer extends Analyzer {

    @Override
    public TokenStream tokenStream(String fieldName, Reader reader) {
        return new LowerCaseFilter(IndexConfiguration.LUCENE_VERSION, 
                new ASCIIFoldingFilter(
                        new StandardTokenizer(IndexConfiguration.LUCENE_VERSION, reader)));
    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
