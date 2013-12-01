package lux.index.field;

import lux.index.FieldRole;
import lux.index.XmlIndexer;
import lux.index.analysis.WhitespaceGapAnalyzer;

import org.apache.lucene.document.Field.Store;

/**
 * Indexes each distinct path as a sequence of name tokens
 */
public class PathField extends FieldDefinition {
    
    public PathField () {
        super (FieldRole.PATH, new WhitespaceGapAnalyzer(), Store.NO, Type.TEXT);
    }
    
    @Override
    public Iterable<?> getValues(XmlIndexer indexer) {
        return indexer.getPathMapper().getPathCounts().keySet();
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */