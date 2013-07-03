package lux.query;

import org.junit.runner.RunWith;

import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.junit.QueryTestRunner;

@RunWith(QueryTestRunner.class)
public class QNameQueryTest {

	// tests are run defined in QNameQueryTest.xml and loaded by the test runner

    public XmlIndexer getIndexer() {
    	return new XmlIndexer(IndexConfiguration.INDEX_QNAMES);
    }
}
