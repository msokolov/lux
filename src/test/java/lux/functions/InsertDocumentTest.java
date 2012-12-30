package lux.functions;

import static lux.index.IndexConfiguration.INDEX_FULLTEXT;
import static lux.index.IndexConfiguration.INDEX_PATHS;
import static lux.index.IndexConfiguration.STORE_XML;
import lux.IndexTestSupport;
import lux.index.XmlIndexer;

import org.apache.lucene.store.RAMDirectory;
import org.junit.BeforeClass;
import org.junit.Test;

public class InsertDocumentTest extends XQueryTest {
    
    private static IndexTestSupport indexTestSupport;
    
    @BeforeClass
    public static void setup() throws Exception {
        RAMDirectory dir = new RAMDirectory();
        indexTestSupport = new IndexTestSupport(new XmlIndexer(INDEX_PATHS|INDEX_FULLTEXT|STORE_XML), dir);
        evaluator = indexTestSupport.makeEvaluator();
    }
    
    @Test
    public void testInsertDocument () throws Exception {
        assertXQuery(null, "lux:insert('/test.xml', <test>this is a test</test>)");
        assertXQuery(null, "doc('/test.xml')", "document '/test.xml' not found");
        assertXQuery(null, "lux:commit()");
        assertXQuery("this is a test", "doc('/test.xml')/test/string()");
        assertXQuery("/test.xml", "lux:search('this is a test')/base-uri()");
        assertXQuery(null, "lux:delete('/test.xml')");
        assertXQuery("true", "doc-available('/test.xml')");
        assertXQuery(null, "lux:commit()");
        assertXQuery("false", "doc-available('/test.xml')");
    }

}
