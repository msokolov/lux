package lux.query;

import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.junit.QueryTestRunner;

import org.junit.runner.RunWith;

@RunWith(QueryTestRunner.class)
public class PathQueryTest extends QNameQueryTest {

	// tests are run defined in QNameQueryTest.xml and loaded by the test runner

    @Override
    public XmlIndexer getIndexer() {
        return new XmlIndexer(IndexConfiguration.INDEX_PATHS | IndexConfiguration.INDEX_FULLTEXT | IndexConfiguration.INDEX_QNAMES);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
