package lux;

import static lux.index.IndexConfiguration.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import javax.xml.stream.XMLStreamException;

import lux.index.XmlIndexer;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.slf4j.LoggerFactory;

public abstract class IndexTestSupportBase {

    private int docLimit;

    protected int totalDocs;
    protected Compiler compiler;
    protected HashMap<String,Integer> elementCounts = new HashMap<String,Integer>();
    
    public static final int QUERY_EXACT = 0x00000001;
    public static final int QUERY_NO_DOCS = 0x00000002;
    public static final int QUERY_MINIMAL = 0x00000004;
    public static final int QUERY_CONSTANT = 0x00000008;

    XmlIndexer indexer;
    
    public IndexTestSupportBase () {
        docLimit = Integer.MAX_VALUE;
        indexer = new XmlIndexer (INDEX_QNAMES|INDEX_PATHS|STORE_DOCUMENT|INDEX_FULLTEXT);
    }
    
    protected abstract void addDocument (String uri, String xml) throws XMLStreamException, IOException;

    protected abstract void commit () throws IOException;
    
    /**
     * index and store all elements of an xml document found on the classpath,
     * remembering the count of each element QName (indexed by ClarkName) in elementCounts
     * 
     * @param filename the pathname of the document to index
     * @throws XMLStreamException
     * @throws IOException
     * @throws SaxonApiException 
     */
    public void indexAllElements(String filename) throws XMLStreamException, IOException, SaxonApiException {
        InputStream in = SearchTest.class.getClassLoader().getResourceAsStream(filename);
        if (in == null) {
            throw new FileNotFoundException (filename + " not found");
        }
        indexAllElements(filename, in);
    }
    
    public void indexAllElements(String uri, InputStream in) throws XMLStreamException, IOException, SaxonApiException {
        indexer.index(in, uri);
        Serializer outputter = new Serializer();
        XdmNode doc = indexer.getXdmNode();
        indexer.reset();
        addDocument ('/' + uri, outputter.serializeNodeToString(doc));
        // index all descendants
        totalDocs = 1;
        elementCounts.clear();
        XdmSequenceIterator iter = doc.axisIterator(Axis.DESCENDANT);
        iter.next(); // skip the root element, we already indexed it
        while (iter.hasNext() && totalDocs < docLimit) {
            XdmNode e = (XdmNode) iter.next();
            if (e.getNodeKind() != XdmNodeKind.ELEMENT) {
                continue;
            }
            Integer count = elementCounts.get (e.getNodeName().getClarkName());
            if (count == null) {
                elementCounts.put (e.getNodeName().getClarkName(), 1);
            } else {
                elementCounts.put (e.getNodeName().getClarkName(), count + 1);
            }
            String xml = outputter.serializeNodeToString(e);
            addDocument ('/' + uri + '-' + totalDocs, xml);
            ++totalDocs;
            if (totalDocs % 50 == 0) {
                // fragment the index
                commit();
            }
        }
        commit();
        LoggerFactory.getLogger(getClass()).info("indexed " + totalDocs + " documents");
    }

    public int getDocLimit() {
        return docLimit;
    }

    public void setDocLimit(int docLimit) {
        this.docLimit = docLimit;
    }

}