package lux;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;

import javax.xml.stream.XMLStreamException;

import lux.api.QueryStats;
import lux.index.XmlIndexer;
import lux.saxon.Saxon;
import lux.saxon.Saxon.Dialect;
import lux.search.LuxSearcher;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

/**
 * Test support class that sets up a lucene index and generates and indexes documents from hamlet.xml.
 */
public class IndexTestSupport {

    Directory dir;
    LuxSearcher searcher;
    XmlIndexer indexer;
    int totalDocs;
    HashMap<String,Integer> elementCounts = new HashMap<String,Integer>();
        
    public final static int QUERY_EXACT = 0x00000001;
    public final static int QUERY_NO_DOCS = 0x00000002;
    public final static int QUERY_MINIMAL = 0x00000004;
    public final static int QUERY_CONSTANT = 0x00000008;

    public IndexTestSupport() throws XMLStreamException, IOException, SaxonApiException {
        this (new XmlIndexer (XmlIndexer.INDEX_QNAMES|XmlIndexer.INDEX_PATHS|XmlIndexer.STORE_XML|
                //0,
                XmlIndexer.INDEX_FULLTEXT), new RAMDirectory());
    }
    
    public IndexTestSupport(XmlIndexer indexer, Directory dir) throws XMLStreamException, IOException, SaxonApiException {
        // create an in-memory Lucene index, index some content
        this.indexer = indexer;
        this.dir = dir;
        indexAllElements (indexer, dir, "lux/hamlet.xml");
        searcher = new LuxSearcher(dir);
    }

    public void close() throws Exception {
        searcher.close();
        dir.close();
    }

    /**
     * index and store all elements of an xml document found on the classpath,
     * remembering the count of each element QName (indexed by ClarkName) in elementCounts
     * 
     * @param filename the pathname of the document to index
     * @throws XMLStreamException
     * @throws IOException
     * @throws SaxonApiException 
     */
    public void indexAllElements(XmlIndexer indexer, Directory dir, String filename) throws XMLStreamException, IOException, SaxonApiException {
        indexAllElements(indexer, dir, filename, SearchTest.class.getClassLoader().getResourceAsStream(filename));
        System.out.println ("Indexed " + totalDocs + " documents from " + filename);
    }
    
    public void indexAllElements(XmlIndexer indexer, Directory dir, String uri, InputStream in) throws XMLStreamException, IOException, SaxonApiException {
        IndexWriter indexWriter = indexer.getIndexWriter(dir);
        String xml = IOUtils.toString(in);
        indexer.indexDocument(indexWriter, '/' + uri, xml);
        Serializer outputter = new Serializer();
        // index all descendants
        totalDocs = 1;
        elementCounts.clear();
        XdmSequenceIterator iter = indexer.getXdmNode().axisIterator(Axis.DESCENDANT);
        iter.next(); // skip the root element, we already indexed it
        while (iter.hasNext()) {
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
            String speech = outputter.serializeNodeToString(e);
            indexer.indexDocument (indexWriter, '/' + uri + '-' + totalDocs, speech);
            ++totalDocs;
        }
        indexWriter.commit();
        indexWriter.close(true);
    }
    
    public Saxon getEvaluator() {
        Saxon eval = new Saxon(searcher, indexer, Dialect.XQUERY_1);
        eval.setQueryStats (new QueryStats());
        return eval;
    }


}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
