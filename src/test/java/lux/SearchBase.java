/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

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

import org.apache.commons.io.IOUtils;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.jdom.Element;
import org.jdom.filter.ElementFilter;
import org.jdom.output.XMLOutputter;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public abstract class SearchBase {

    private static Directory dir;
    protected static LuxSearcher searcher;
    protected static XmlIndexer indexer;
    protected static int totalDocs;
    protected static int QUERY_EXACT = 0x00000001;
    protected static int QUERY_NO_DOCS = 0x00000002;
    protected static int QUERY_MINIMAL = 0x00000004;
    protected static int QUERY_CONSTANT = 0x00000008;

    @BeforeClass
    public static void setUp() throws Exception {
        // create an in-memory Lucene index, index some content
        dir = new RAMDirectory();
        indexer = new XmlIndexer ();
        indexAllElements (indexer, dir, "lux/hamlet.xml");
        searcher = new LuxSearcher(dir);
    }

    @AfterClass
    public static void tearDown() throws Exception {
        searcher.close();
        dir.close();
    }

    protected static HashMap<String,Integer> elementCounts = new HashMap<String,Integer>();
    
    /**
     * index and store all elements of an xml document found on the classpath
     * 
     * @param filename the pathname of the document to index
     * @throws XMLStreamException
     * @throws IOException
     */
    public static void indexAllElements(XmlIndexer indexer, Directory dir, String filename) throws XMLStreamException, IOException {
        indexAllElements(indexer, dir, filename, SearchTest.class.getClassLoader().getResourceAsStream(filename));
        System.out.println ("Indexed " + totalDocs + " documents from " + filename);
    }
    
    public static void indexAllElements(XmlIndexer indexer, Directory dir, String uri, InputStream in) throws XMLStreamException, IOException {
        IndexWriter indexWriter = indexer.getIndexWriter(dir);
        String xml = IOUtils.toString(in);
        indexer.indexDocument(indexWriter, uri, xml);
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
            ++totalDocs;
            String speech = outputter.outputString(e);
            indexer.indexDocument (indexWriter, uri + '-' + totalDocs, speech);
        }
        indexWriter.commit();
        indexWriter.close();
    }
    
    public static Saxon getEvaluator() {
        Saxon eval = new Saxon(searcher, indexer);
        eval.setQueryStats (new QueryStats());
        return eval;
    }


}/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
