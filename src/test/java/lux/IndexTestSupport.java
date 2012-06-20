package lux;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;

import javax.xml.stream.XMLStreamException;

import lux.api.QueryStats;
import lux.index.XmlIndexer;
import lux.lucene.LuxSearcher;
import lux.saxon.Saxon;
import lux.saxon.Saxon.Dialect;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.jdom.Element;
import org.jdom.filter.ElementFilter;
import org.jdom.output.XMLOutputter;

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

    public IndexTestSupport() throws XMLStreamException, IOException {
        this (XmlIndexer.INDEX_QNAMES|XmlIndexer.INDEX_PATHS|XmlIndexer.STORE_XML|XmlIndexer.BUILD_JDOM|
                //0,
                 XmlIndexer.INDEX_FULLTEXT,
              new RAMDirectory());
    }
    
    public IndexTestSupport(int options, Directory dir) throws XMLStreamException, IOException {
        // create an in-memory Lucene index, index some content
        indexer = new XmlIndexer (options);
        this.dir = dir;
        indexAllElements (indexer, dir, "lux/hamlet.xml");
        searcher = new LuxSearcher(dir);
    }

    public void close() throws Exception {
        searcher.close();
        dir.close();
    }

    /**
     * index and store all elements of an xml document found on the classpath
     * 
     * @param filename the pathname of the document to index
     * @throws XMLStreamException
     * @throws IOException
     */
    public void indexAllElements(XmlIndexer indexer, Directory dir, String filename) throws XMLStreamException, IOException {
        indexAllElements(indexer, dir, filename, SearchTest.class.getClassLoader().getResourceAsStream(filename));
        System.out.println ("Indexed " + totalDocs + " documents from " + filename);
    }
    
    public void indexAllElements(XmlIndexer indexer, Directory dir, String uri, InputStream in) throws XMLStreamException, IOException {
        IndexWriter indexWriter = indexer.getIndexWriter(dir);
        String xml = IOUtils.toString(in);
        indexer.indexDocument(indexWriter, '/' + uri, xml);
        XMLOutputter outputter = new XMLOutputter();
        // index all descendants
        totalDocs = 1;
        elementCounts.clear();
        Iterator<?> iter = indexer.getJDOM().getDescendants(new ElementFilter());
        iter.next(); // skip the root element, we already indexed it
        while (iter.hasNext()) {
            Element e = (Element) iter.next();
            Integer count = elementCounts.get (e.getName());
            if (count == null) {
                elementCounts.put (e.getName(), 1);
            } else {
                elementCounts.put (e.getName(), count + 1);
            }
            String speech = outputter.outputString(e);
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
