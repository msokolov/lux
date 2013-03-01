package lux.index.field;

import java.util.Collections;
import java.util.Set;

import lux.index.XmlIndexer;

import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;

public class URIField extends FieldDefinition {
    
    private static final URIField instance = new URIField();
    
    public static URIField getInstance() {
        return instance;
    }
    
    protected URIField () {
        super ("lux_uri", new KeywordAnalyzer(), Store.YES, Type.STRING, true);
    }
    
    @Override
    public Set<StringField> getFieldValues(XmlIndexer indexer) {
        return Collections.singleton(new StringField(indexer.getConfiguration().getFieldName(this), indexer.getURI(),Store.YES)); 
    }
    
    @Override
    public boolean isSingleValued() {
        return true;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
