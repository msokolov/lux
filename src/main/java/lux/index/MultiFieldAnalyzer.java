package lux.index;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.analysis.TokenStream;

/**
 * Like SolrIndexAnalyzer, but without the rest of solr, and supports field name prefixes that
 * map to many fields (somewhat like Solr's dynamic fields).
 * If no analyzer is registered for a field name, and no prefix matches the field name,
 * a default analyzer is returned.  The
 * default analyzer is the analyzer mapped to the null key.  By default, null is mapped to
 * an instance of KeywordAnalyzer (an analyzer that returns a single token for the entire field value).
 */
public final class MultiFieldAnalyzer extends Analyzer {
    
    private HashMap<String,Analyzer> analyzers;
    private HashMap<String,Analyzer> analyzerPrefixes;
    
    public MultiFieldAnalyzer () {
        analyzers = new HashMap<String,Analyzer>();
        analyzerPrefixes = new HashMap<String,Analyzer>();
        analyzers.put(null, new KeywordAnalyzer());
    }
    
    public void put (String fieldName, Analyzer analyzer) {
        if (analyzer == null) {
            analyzers.remove(fieldName);
        } else {
            analyzers.put(fieldName, analyzer);
        }
    }
    
    public void putPrefix (String prefix, Analyzer analyzer) {
        if (analyzer == null) {
            analyzerPrefixes.remove(prefix);
        }
        analyzerPrefixes.put(prefix, analyzer);
    }

    protected Analyzer getAnalyzer(String fieldName)
    {
        if (analyzers.containsKey(fieldName)) {
            return analyzers.get(fieldName);
        }
        for (Map.Entry<String, Analyzer> entry : analyzerPrefixes.entrySet()) {
            if (fieldName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return analyzers.get(null);        
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

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
