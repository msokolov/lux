/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xqts;

import lux.api.QueryStats;
import lux.api.ResultSet;
import lux.index.XmlIndexer;
import lux.lucene.LuxSearcher;
import lux.saxon.Saxon;
import lux.saxon.SaxonContext;
import lux.saxon.SaxonExpr;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Test;

public class TestOneCase {

    @Test public void testOne () throws Exception {
        Catalog catalog = new Catalog ("/users/sokolov/workspace/XQTS_1_0_3");
        TestCase test1 = catalog.getTestCaseByName("op-date-greater-than2args-1");
        System.out.println (test1.getPath());
        
        RAMDirectory dir = new RAMDirectory();
        XmlIndexer indexer = new XmlIndexer ();
        IndexWriter indexWriter = indexer.getIndexWriter(dir);
        indexer.indexDocument (indexWriter, test1.getInputText());
        indexWriter.commit();
        indexWriter.close();
        LuxSearcher searcher = new LuxSearcher(dir);
        Saxon eval = new Saxon();
        eval.setContext(new SaxonContext(searcher, indexer));
        eval.setQueryStats (new QueryStats());
        SaxonExpr expr = (SaxonExpr) eval.compile(test1.getQueryText());
        System.out.println (expr.getXPathExecutable().getUnderlyingExpression().getInternalExpression());
        ResultSet<?> results = (ResultSet<?>) eval.evaluate(expr);
        QueryStats stats = eval.getQueryStats();
        System.out.println ("Got " + results.size() + " results in " + stats.totalTime + "ms");
        // TODO:
        // 1) create a Lucene Directory
        // 2) write / index the input document to it
        // 3) modify the query, replacing the global variable with collection()
        // 4) exec the query
        // 5) read the expected result
        // 6) compare the result against the expected result
        // 7) log the outcome
    }
}
