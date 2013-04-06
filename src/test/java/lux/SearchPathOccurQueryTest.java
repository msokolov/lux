package lux;

import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;

import org.apache.lucene.store.RAMDirectory;
import org.junit.BeforeClass;

public class SearchPathOccurQueryTest extends SearchPathQueryTest {

    @BeforeClass
    public static void setupClass () throws Exception {
        SearchPathQueryTest.setupClass();
        // QName index only:
        // baselineIndexer = new XmlIndexer(IndexConfiguration.DEFAULT_OPTIONS & ~IndexConfiguration.INDEX_PATHS);
        // baselineIndexer = new XmlIndexer(IndexConfiguration.DEFAULT_OPTIONS);
        indexer = new XmlIndexer(IndexConfiguration.DEFAULT_OPTIONS | IndexConfiguration.INDEX_EACH_PATH);
        index = new IndexTestSupport("lux/hamlet.xml", indexer, new RAMDirectory());
        // indexer = new XmlIndexer();
        // repeatCount = 500;
        // repeatCount = 5;
    }

    @Override
    public String getQueryXml (Q q) {
        switch (q) {
        case ACT_SCENE:
            return "<RegexpQuery fieldName=\"lux_path\">SCENE\\/ACT(\\/.*)?</RegexpQuery>";
        case SCENE: return "<RegexpQuery fieldName=\"lux_path\">SCENE(\\/.*)?</RegexpQuery>";
        case ACT: return "<RegexpQuery fieldName=\"lux_path\">ACT(\\/.*)?</RegexpQuery>";
        default: return super.getQueryXml(q);
        }
    }

}
