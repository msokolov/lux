package lux.index.field;

import java.io.IOException;
import java.util.Collections;

import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.index.analysis.DefaultAnalyzer;
import lux.index.analysis.ElementTokenStream;
import lux.index.analysis.QNameTokenFilter;
import lux.xml.SaxonDocBuilder;
import net.sf.saxon.s9api.XdmNode;

import org.apache.commons.io.input.CharSequenceReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.document.Fieldable;

/**
 * Indexes the text in each element of a document
 */
public class ElementTextField extends FieldDefinition {
    
    private static final ElementTextField instance = new ElementTextField();
        
    public static ElementTextField getInstance() {
        return instance;
    }
    
    protected ElementTextField () {
        // TODO - enable caller to supply analyzer (extending our analyzer so we can ensure that
        // element/attribute text tokens are generated)
        super ("lux_elt_text", new DefaultAnalyzer(), Store.NO, Type.TOKENS, TermVector.NO);
    }
    
    @Override
    public Iterable<Fieldable> getFieldValues(XmlIndexer indexer) {
        XdmNode doc = indexer.getXdmNode();
        if (doc != null && doc.getUnderlyingNode() != null) {
            SaxonDocBuilder builder = indexer.getSaxonDocBuilder();
            String fieldName = indexer.getConfiguration().getFieldName(this);
            Analyzer analyzer = getAnalyzer();
            TokenStream textTokens=null;
            try {
                textTokens = analyzer.reusableTokenStream(fieldName, new CharSequenceReader(""));
            } catch (IOException e) { }
 
            ElementTokenStream tokens = new ElementTokenStream (fieldName, analyzer, textTokens, doc, builder.getOffsets());
            ((QNameTokenFilter) tokens.getWrappedTokenStream()).setNamespaceAware(indexer.getConfiguration().isOption(IndexConfiguration.NAMESPACE_AWARE));
            return new FieldValues (indexer.getConfiguration(), this, Collections.singleton(
                        new Field(indexer.getConfiguration().getFieldName(this), tokens, getTermVector())));
        }
        return Collections.emptySet();
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
