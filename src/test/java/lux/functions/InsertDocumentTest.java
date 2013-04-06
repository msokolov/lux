package lux.functions;

import static lux.index.IndexConfiguration.*;
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
        indexTestSupport = new IndexTestSupport(new XmlIndexer(INDEX_PATHS|INDEX_FULLTEXT|STORE_DOCUMENT), dir);
        evaluator = indexTestSupport.makeEvaluator();
    }
    
    @Test
    public void testInsertDocument () throws Exception {
        assertXQuery(null, "lux:insert('/test.xml', <test>this is a test</test>)");
        assertXQuery(null, "doc('/test.xml')", "document not found: /test.xml");
        assertXQuery(null, "lux:commit()");
        evaluator.reopenSearcher(); // need to do this to see the updates
        assertXQuery("this is a test", "doc('/test.xml')/test/string()");
        assertXQuery("lux://test.xml", "lux:search('this is a test')/base-uri()");
        // find document with no scheme in URI
        assertXQuery("this is a test", "doc('/test.xml')/string()");
        // find document *with* scheme in URI
        assertXQuery("this is a test", "doc('lux://test.xml')/string()");
        assertXQuery(null, "lux:delete('/test.xml')");
        assertXQuery("true", "doc-available('/test.xml')");
        assertXQuery(null, "lux:commit()");
        evaluator.reopenSearcher(); // need to do this to see the updates
        assertXQuery("false", "doc-available('/test.xml')");
    }
    
    @Test
    public void deleteAll () throws Exception {
        assertXQuery(null, "lux:insert('/test.xml', <test>this is a test</test>)");
        assertXQuery(null, "lux:commit()");
        evaluator.reopenSearcher(); // need to do this to see the updates
        assertXQuery("this is a test", "doc('/test.xml')/test/string()");

        assertXQuery(null, "lux:delete('lux:/')"); // deletes all documents
        // by calling evaluator.getDocWriter().deleteAll();
        
        assertXQuery("true", "doc-available('/test.xml')");
        assertXQuery(null, "lux:commit()");
        evaluator.reopenSearcher(); // need to do this to see the updates
        assertXQuery("false", "doc-available('/test.xml')");
    	
    }

}
