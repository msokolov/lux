package lux.index.analysis;

import java.io.Reader;

import lux.index.XmlIndexer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardTokenizer;

public final class DefaultAnalyzer extends Analyzer {

    // TODO wrap an existing Analyzer
    
    @Override
    public TokenStream tokenStream(String fieldName, Reader reader) {
        return new LowerCaseFilter(XmlIndexer.LUCENE_VERSION, new StandardTokenizer(XmlIndexer.LUCENE_VERSION, reader));
    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
