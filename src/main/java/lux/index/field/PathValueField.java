package lux.index.field;

import java.util.Collections;

import lux.index.XPathValueMapper;
import lux.index.XmlIndexer;
import lux.index.analysis.PathValueTokenStream;

import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.util.Version;

public class PathValueField extends XmlField {
    
    private static final PathValueField instance = new PathValueField();
    
    public static PathValueField getInstance() {
        return instance;
    }
    
    protected PathValueField () {
        super ("lux_path", new WhitespaceAnalyzer(Version.LUCENE_34), Store.NO, Type.TOKENS);
    }
    
    @Override
    public Iterable<Fieldable> getFieldValues(XmlIndexer indexer) {
        // replace with a custom Fieldable
        XPathValueMapper mapper = (XPathValueMapper) indexer.getPathMapper();        
        return new FieldValues (this, Collections.singleton
                (new TokenizedField(getName(), 
                        new PathValueTokenStream
                        (mapper.getPathValues()), Store.NO, Index.ANALYZED, TermVector.NO)));
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
