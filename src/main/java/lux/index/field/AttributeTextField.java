package lux.index.field;

import java.util.Collections;

import lux.index.XmlIndexer;
import lux.index.analysis.AttributeTokenStream;
import lux.index.analysis.DefaultAnalyzer;
import lux.query.ParseableQuery;
import lux.query.QNameTextQuery;
import lux.xml.SaxonDocBuilder;
import lux.xpath.QName;
import net.sf.saxon.s9api.XdmNode;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.index.Term;

/**
 * Indexes the text in each element of a document
 */
public class AttributeTextField extends XmlField {
    
    private static final AttributeTextField instance = new AttributeTextField();
        
    public static AttributeTextField getInstance() {
        return instance;
    }
    
    protected AttributeTextField () {
        super ("lux_att_text", new DefaultAnalyzer(), Store.NO, Type.TOKENS, TermVector.NO);
    }
    
    @Override
    public Iterable<Field> getFieldValues(XmlIndexer indexer) {
        XdmNode doc = indexer.getXdmNode();
        SaxonDocBuilder builder = indexer.getSaxonDocBuilder();
        AttributeTokenStream tokens = new AttributeTokenStream(doc, builder.getOffsets());
        return new FieldValues (this, Collections.singleton(
                        new Field(getName(), tokens, getTermVector())));
    }
    
    public ParseableQuery makeAttributeValueQuery (QName qname, String value) {
        return new QNameTextQuery(new Term(getName(), value), qname.getEncodedName());
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
