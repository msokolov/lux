package lux;

import org.junit.BeforeClass;

public class SearchPathOccurQueryTest extends SearchPathQueryTest {

    @BeforeClass
    public static void setupClass () throws Exception {
        SearchPathQueryTest.setupClass();
        //baselineIndexer = new XmlIndexer(IndexConfiguration.DEFAULT_OPTIONS);
        //indexer = new XmlIndexer(IndexConfiguration.DEFAULT_OPTIONS);
        //indexer = new XmlIndexer(IndexConfiguration.DEFAULT_OPTIONS | IndexConfiguration.INDEX_EACH_PATH);
    }
    
    @Override
    public String getQueryXml (Q q) {
        switch (q) {
        case ACT_SCENE:
            return "<RegexpQuery fieldName=\"lux_path\">SCENE\\/ACT\\/.*</RegexpQuery>";
        case SCENE: return "<RegexpQuery fieldName=\"lux_path\">SCENE\\/.*</RegexpQuery>";
        case ACT: return "<RegexpQuery fieldName=\"lux_path\">ACT\\/.*</RegexpQuery>";
        default: return super.getQueryXml(q);
        }
    }

}
