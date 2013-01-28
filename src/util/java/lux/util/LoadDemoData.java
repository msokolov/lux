package lux.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import lux.index.XmlIndexer;
import net.sf.saxon.s9api.SaxonApiException;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.SimpleFSDirectory;

/**
 * Accepts the name of a Lucene index data directory and loads the exploded hamlet.xml 
 * sample data to it.
 */
public class LoadDemoData {
    public static void main (String [] argv) throws XMLStreamException, IOException, SaxonApiException {
        String indexDir;
        if (argv.length != 1) {
            // System.err.println ("Usage: java LoadDemoData <index directory>");
            indexDir = "solr/data/index";
        } else {
            indexDir = argv[0];
        }
        Directory index = new SimpleFSDirectory (new File(indexDir));
        String dataDir = "samples/wikipedia";
        XmlIndexer indexer = new XmlIndexer();
        // indexer.getXmlReader().setStripNamespaces(true);
        IndexWriter indexWriter = indexer.getIndexWriter(index);
        indexWriter.deleteAll();
        index (indexer, indexWriter, dataDir);
        indexWriter.commit();
        indexWriter.close(true);
    }

    private static void index(XmlIndexer indexer, IndexWriter indexWriter, String path) throws IOException, XMLStreamException {
        File file = new File (path);
        if (file.isFile()) {
            if (path.matches(".*\\.xml$")) {
                System.out.println (path);
                indexer.indexDocument(indexWriter, path, new FileInputStream (file));
            } else {
                System.out.println ("skipping " + path);
            }
        } else {
            for (String child : file.list()) {
                index (indexer, indexWriter, path + '/' + child);
            }
        }
    }
}
