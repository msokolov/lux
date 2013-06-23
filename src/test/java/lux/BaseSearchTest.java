package lux;

import static lux.IndexTestSupport.*;
import static lux.index.IndexConfiguration.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Properties;

import javax.xml.transform.stream.StreamResult;

import lux.exception.LuxException;
import lux.index.XmlIndexer;
import lux.index.field.FieldDefinition.Type;
import lux.index.field.XPathField;
import lux.xml.QName;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.XPathException;

import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.RAMDirectory;
import org.junit.AfterClass;

public abstract class BaseSearchTest {
    
    static final int MIL = 1000000;
    protected static IndexTestSupport index;
    protected static int totalDocs;

    public static void setup(String ... xmlfile) throws Exception {
        XmlIndexer indexer = new XmlIndexer (INDEX_QNAMES|INDEX_PATHS|STORE_DOCUMENT|INDEX_FULLTEXT|STORE_TINY_BINARY);
        indexer.getConfiguration().addField(new XPathField<String>("doctype", "name(/*)", null, Store.YES, Type.STRING));
        indexer.getConfiguration().addField(new XPathField<Integer>("actnum", "/*/@act", null, Store.YES, Type.INT));
        indexer.getConfiguration().addField(new XPathField<Integer>("scnlong", "/*/@scene", null, Store.YES, Type.LONG));
        indexer.getConfiguration().addField(new XPathField<String>("actstr", "/*/@act", null, Store.YES, Type.STRING));
        index = new IndexTestSupport(xmlfile, indexer, new RAMDirectory());
        
        totalDocs= index.totalDocs;
    }

    @AfterClass
    public static void tearDown() throws Exception {
        index.close();
    }

    public BaseSearchTest() {
        super();
    }

    protected void assertResultValue(XdmResultSet results, int sceneDocCount) {
        assertEquals (String.valueOf(sceneDocCount), results.iterator().next().toString());
    }

    protected XdmResultSet assertSearch(String query) throws Exception {
        return assertSearch (query, 0);
    }

    protected XdmResultSet assertSearch(String query, Integer props) throws Exception {
        return assertSearch(query, props, null);
    }

    protected XdmResultSet assertSearch(String query, Integer props, Integer docCount) throws Exception {
        return assertSearch (query, props, docCount, null);
    }

    protected XdmResultSet assertSearch(String expectedResult, String query, Integer facts, Integer docCount) throws Exception {
        return assertSearch (expectedResult, query, facts, docCount, null);
    }

    protected XdmResultSet assertSearch(String expectedResult, String query, Integer props, Integer docCount, Integer cacheMisses) throws Exception {
        XdmResultSet results = assertSearch (query, props, docCount, cacheMisses);
        if (! results.getErrors().isEmpty()) {
            throw results.getErrors().iterator().next();
        }
        boolean hasResults = results.iterator().hasNext();
        String result = null;
        if (hasResults) {
            XdmItem item = results.iterator().next();
            if (item instanceof XdmNode) {
                result = serialize (((XdmNode)item).getUnderlyingNode());
            } else {
                result = item.toString();
            }
        }
        if (expectedResult == null) {            
            assertTrue ("results not empty, got: " + result, !hasResults);
            return results;
        }
        assertTrue ("no results", hasResults);
        assertEquals ("incorrect query result", expectedResult, result);
        return results;
    }
    
    public static String serialize(/*@NotNull*/ NodeInfo nodeInfo) throws XPathException {
        StringWriter sw = new StringWriter();
        Properties props = new Properties();
        props.setProperty("method", "xml");
        props.setProperty("indent", "no");
        props.setProperty("omit-xml-declaration", "yes");
        QueryResult.serialize(nodeInfo, new StreamResult(sw), props);
        return sw.toString();
    }

    /**
     * Executes the query, ensures that the given properties hold, and returns the result set.
     * Prints some diagnostic statistics, including total elapsed time (t) and time spent in the 
     * search result collector (tsearch), which excludes any subseuqnet evaluation of results.
     * @param query an XPath query
     * @param props properties asserted to hold for the query evaluation
     * @return the query results
     * @throws LuxException
     * @throws IOException 
     * @throws LockObtainFailedException 
     * @throws CorruptIndexException 
     */
    protected XdmResultSet assertSearch(String query, Integer props, Integer docCount, Integer cacheMisses) throws LuxException,
            CorruptIndexException, LockObtainFailedException, IOException {
        Evaluator eval = index.makeEvaluator();
        XQueryExecutable expr = eval.getCompiler().compile(query);
        QueryContext qc = new QueryContext();
        qc.bindVariable(new QName("id"), new XdmAtomicValue("test"));   // bind this random id so we can use it in tests???
        XdmResultSet results = (XdmResultSet) eval.evaluate(expr, qc);
        if (! results.getErrors().isEmpty()) {
            throw new LuxException (results.getErrors().get(0).getMessage());
        }
        QueryStats stats = eval.getQueryStats();
        System.out.println (String.format("t=%d, tsearch=%d, tretrieve=%d, query=%s", 
                stats.totalTime/MIL, stats.collectionTime/MIL, stats.retrievalTime/MIL, query));
        System.out.println ("optimized query=" + stats.optimizedQuery);
        System.out.println (String.format("cache hits=%d, misses=%d", 
                eval.getDocReader().getCacheHits(), eval.getDocReader().getCacheMisses()));
        if (props != null) {
            if ((props & QUERY_EXACT) != 0) {
                assertEquals ("query is not exact", results.size(), stats.docCount);
            }
            if ((props & QUERY_CONSTANT) != 0) {
                assertEquals ("query is not constant", 0, stats.docCount);
            }
            if ((props & QUERY_MINIMAL) != 0) {
                // this is not the same as minimal, but is implied by it:
                assertTrue ("query is not minimal; retrieved " + stats.docCount + 
                        " docs but only got " + results.size() + " results", 
                        results.size() >= stats.docCount);
                // in addition we'd need to show that every document produced at least one result
            }
            if ((props & QUERY_NO_DOCS) != 0) {
                // This only makes sense because the main cost is usually retrieving and parsing documents
                // if we spend most of our time searching (in the collector), we didn't do a lot of xquery evaluation
                assertTrue ("query is not filter free", stats.retrievalTime / (stats.totalTime + 1.0) < 0.01);
            }
        }
        if (docCount != null) {
            assertEquals ("incorrect document result count", docCount.intValue(), stats.docCount);
        }
        if (cacheMisses != null) {
            assertEquals ("incorrect cache misses count", cacheMisses.intValue(), eval.getDocReader().getCacheMisses());            
        }
        return results;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
