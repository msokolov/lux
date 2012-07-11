package lux.index.field;

import java.util.Collections;

import lux.index.XmlIndexer;
import lux.index.analysis.ElementTokenStream;
import lux.index.analysis.DefaultAnalyzer;
import lux.query.ParseableQuery;
import lux.query.QNameTextQuery;
import lux.xml.SaxonDocBuilder;
import lux.xpath.QName;
import net.sf.saxon.s9api.XdmNode;

import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.Term;

/**
 * Indexes the text in each element of a document
 */
public class ElementTextField extends XmlField {
    
    private static final ElementTextField instance = new ElementTextField();
        
    public static ElementTextField getInstance() {
        return instance;
    }
    
    protected ElementTextField () {
        // TODO - better default analyzer w/stemming + diacritic normalization
        // TODO - enable caller to supply analyzer (extending our analyzer so we can ensure that
        // element/attribute text tokens are generated)
        super ("lux_elt_text", new DefaultAnalyzer(), Store.NO, Type.TOKENS, TermVector.NO);
    }
    
    @Override
    public Iterable<Fieldable> getFieldValues(XmlIndexer indexer) {
        XdmNode doc = indexer.getXdmNode();
        SaxonDocBuilder builder = indexer.getSaxonDocBuilder();
        ElementTokenStream tokens = new ElementTokenStream (doc, builder.getOffsets());
        return new FieldValues (this, Collections.singleton(
                        new TokenizedField(getName(), tokens, 
                        Store.NO, Index.ANALYZED, getTermVector())));
    }
    
    public ParseableQuery makeElementValueQuery (QName qname, String value) {
        return new QNameTextQuery(new Term(getName(), value), qname.getEncodedName());
    }

    public ParseableQuery makeAttributeValueQuery (QName qname, String value) {
        return new QNameTextQuery(new Term(getName(), value), "@" + qname.getEncodedName());
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
