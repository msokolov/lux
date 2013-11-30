package lux.index.field;

import java.util.Collections;

import lux.index.FieldRole;
import lux.index.XPathValueMapper;
import lux.index.XmlIndexer;
import lux.index.analysis.PathValueTokenStream;

import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.util.Version;

public class PathValueField extends FieldDefinition {
    
    public PathValueField () {
        super (FieldRole.PATH_VALUE, new WhitespaceAnalyzer(Version.LUCENE_41), Store.NO, Type.TOKENS);
    }
    
    @Override
    public Iterable<IndexableField> getFieldValues(XmlIndexer indexer) {
        // replace with a custom Fieldable
        XPathValueMapper mapper = (XPathValueMapper) indexer.getPathMapper();        
        return new FieldValues (this, Collections.singleton
                (new TextField(getName(), new PathValueTokenStream(mapper.getPathValues()))));
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
