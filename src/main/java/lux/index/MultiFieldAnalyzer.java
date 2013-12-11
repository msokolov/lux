package lux.index;

import java.util.HashMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.AnalyzerWrapper;
import org.apache.lucene.analysis.core.KeywordAnalyzer;

/**
 * Like SolrIndexAnalyzer, but without dependencies on solr  The
 * default analyzer is the analyzer mapped to the null key.  By default, null is mapped to
 * an instance of KeywordAnalyzer (an analyzer that returns a single token for the entire field value).
 */
public final class MultiFieldAnalyzer extends AnalyzerWrapper {
    
    private HashMap<String,Analyzer> analyzers;
    
    public MultiFieldAnalyzer () {
        super(Analyzer.PER_FIELD_REUSE_STRATEGY);
        analyzers = new HashMap<String,Analyzer>();
        analyzers.put(null, new KeywordAnalyzer());
    }
    
    public void put (String fieldName, Analyzer analyzer) {
        if (analyzer == null) {
            analyzers.remove(fieldName);
        } else {
            analyzers.put(fieldName, analyzer);
        }
    }
    
    @Override
    public Analyzer getWrappedAnalyzer(String fieldName)
    {
        if (analyzers.containsKey(fieldName)) {
            return analyzers.get(fieldName);
        }
        return analyzers.get(null);        
    }

    @Override
    protected TokenStreamComponents wrapComponents(String fieldName, TokenStreamComponents components) {
        return components;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
