package lux.query;

import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;

public class PathOccurrenceQueryTest extends QNameQueryTest {
    
    @Override
    public XmlIndexer getIndexer() {
        return new XmlIndexer(IndexConfiguration.INDEX_PATHS | IndexConfiguration.INDEX_FULLTEXT | IndexConfiguration.INDEX_EACH_PATH);
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
