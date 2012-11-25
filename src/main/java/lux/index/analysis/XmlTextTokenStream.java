package lux.index.analysis;

import lux.index.IndexConfiguration;
import lux.xml.Offsets;
import net.sf.saxon.s9api.XdmNode;

import org.apache.lucene.analysis.LowerCaseFilter;

/**
 * TODO: wrap an entire Analyzer, not just a StandardTokenizer
 */
public final class XmlTextTokenStream extends TextOffsetTokenStream {

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
