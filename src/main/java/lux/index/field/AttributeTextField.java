package lux.index.field;

import java.util.Collections;

import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.index.analysis.AttributeTokenStream;
import lux.index.analysis.DefaultAnalyzer;
import lux.index.analysis.QNameTokenFilter;
import lux.xml.SaxonDocBuilder;
import net.sf.saxon.s9api.XdmNode;

import org.apache.commons.io.input.CharSequenceReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Fieldable;

/**
 * Indexes the text in each attribute of a document
 */
public class AttributeTextField extends FieldDefinition {
    
    private static final AttributeTextField instance = new AttributeTextField();
        
    public static AttributeTextField getInstance() {
        return instance;
    }
    
    protected AttributeTextField () {
        super ("lux_att_text", new DefaultAnalyzer(), Store.NO, Type.TOKENS);
    }
    
    @Override
    public Iterable<? extends Fieldable> getFieldValues(XmlIndexer indexer) {
        XdmNode doc = indexer.getXdmNode();
        if (doc != null && doc.getUnderlyingNode() != null) {
            SaxonDocBuilder builder = indexer.getSaxonDocBuilder();
            String fieldName = indexer.getConfiguration().getFieldName(this);
            Analyzer analyzer = getAnalyzer();
            TokenStream textTokens = analyzer.tokenStream(fieldName, new CharSequenceReader(""));
            AttributeTokenStream tokens = new AttributeTokenStream(fieldName, analyzer, textTokens, doc, builder.getOffsets());
            ((QNameTokenFilter) tokens.getWrappedTokenStream()).setNamespaceAware(indexer.getConfiguration().isOption(IndexConfiguration.NAMESPACE_AWARE));
            return new FieldValues (indexer.getConfiguration(), this, Collections.singleton(
                        new Field(indexer.getConfiguration().getFieldName(this), tokens)));
        }
        return Collections.emptySet();
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
