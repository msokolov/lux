package lux.index.analysis;

import lux.index.IndexConfiguration;
import lux.xml.Offsets;
import net.sf.saxon.s9api.XdmNode;

import org.apache.lucene.analysis.LowerCaseFilter;

/**
 * Extracts tokens from an s9api XML document tree (XdmNode) in order to make them
 * available to Lucene classes that accept TokenStreams, like the indexer and highlighter.
 * 
 * TODO: wrap an entire Analyzer, not just predefined analysis: LowerCaseFilter/StandardTokenizer
 */
public final class XmlTextTokenStream extends TextOffsetTokenStream {

    /**
     * Creates a TokenStream returning tokens drawn from the text content of the document.
     * @param doc tokens will be drawn from all of the text in this document
     * @param offsets if provided, character offsets are captured in this object
     * In theory this can be used for faster highlighting, but until that is proven, 
     * this should always be null.
     */
    public XmlTextTokenStream(XdmNode doc, Offsets offsets) {
        super(doc, offsets);
        contentIter = new ContentIterator(doc);
        wrapped = new LowerCaseFilter(IndexConfiguration.LUCENE_VERSION, tokenizer);
    }

    @Override
    void updateNodeAtts() {
    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
