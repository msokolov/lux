package lux;

import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;

public class QNameTextQueryTest extends QNameQueryTest {

    @Override
    public XmlIndexer getIndexer() {
        return new XmlIndexer(IndexConfiguration.INDEX_QNAMES | IndexConfiguration.INDEX_FULLTEXT);
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
