package lux.query;

import org.junit.runner.RunWith;

import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.junit.QueryTestRunner;

@RunWith(QueryTestRunner.class)
public class PathQueryTest extends QNameQueryTest {

	// tests are run defined in QNameQueryTest.xml and loaded by the test runner

    public XmlIndexer getIndexer() {
        return new XmlIndexer(IndexConfiguration.INDEX_PATHS | IndexConfiguration.INDEX_FULLTEXT);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
