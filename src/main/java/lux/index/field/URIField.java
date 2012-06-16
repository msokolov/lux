package lux.index.field;

import java.util.Collections;

import lux.index.XmlIndexer;

import org.apache.lucene.analysis.KeywordAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Fieldable;

public class URIField extends XmlField {
    
    private static final URIField instance = new URIField();
    
    public static URIField getInstance() {
        return instance;
    }
    
    protected URIField () {
        super ("lux_uri", new KeywordAnalyzer(), Store.YES, Type.STRING);
    }
    
    @Override
    public Iterable<Fieldable> getFieldValues(XmlIndexer indexer) {
        return Collections.singleton((Fieldable) new Field (XmlField.URI.getName(), indexer.getURI(), 
                Field.Store.YES, Field.Index.NOT_ANALYZED_NO_NORMS));
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
