package lux;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import lux.index.XmlIndexer;

import org.apache.lucene.store.RAMDirectory;
import org.junit.Test;

public abstract class IndexTest {
    
    @Test
    public void testIndexSizes () throws Exception {
        testIndex ("paths", XmlIndexer.INDEX_PATHS | XmlIndexer.BUILD_JDOM);
        testIndex ("qnames", XmlIndexer.INDEX_QNAMES | XmlIndexer.BUILD_JDOM);
        testIndex ("qnames and paths", XmlIndexer.INDEX_QNAMES | XmlIndexer.INDEX_PATHS | XmlIndexer.BUILD_JDOM);
        testIndex ("xml storage", XmlIndexer.STORE_XML| XmlIndexer.BUILD_JDOM);
    }
    
    private void testIndex (String desc, int options) throws XMLStreamException, IOException {
        RAMDirectory dir = new RAMDirectory();
        XmlIndexer indexer = new XmlIndexer (options);
        long t0 = System.currentTimeMillis();
        SearchBase.indexAllElements (indexer, dir, "lux/hamlet.xml");
        System.out.println 
             (String.format("indexed %s in %d ms %d bytes", desc, 
                     (System.currentTimeMillis()-t0), dir.sizeInBytes()));
        dir.close();
    }
}
