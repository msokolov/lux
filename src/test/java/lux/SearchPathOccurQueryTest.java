package lux;

import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;

public class SearchPathOccurQueryTest extends SearchPathQueryTest {

	@Override public XmlIndexer getIndexer () {
		return new XmlIndexer(IndexConfiguration.DEFAULT_OPTIONS | IndexConfiguration.INDEX_EACH_PATH);
	}

}
