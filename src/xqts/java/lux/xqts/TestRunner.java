package lux.xqts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;

import lux.api.QueryStats;
import lux.api.ResultSet;
import lux.index.XmlIndexer;
import lux.lucene.LuxSearcher;
import lux.saxon.Saxon;
import lux.saxon.SaxonContext;
import lux.saxon.SaxonExpr;
import lux.xqts.TestCase.ComparisonMode;

import net.sf.saxon.s9api.XdmItem;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.RAMDirectory;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestRunner {

    protected static Catalog catalog;
    private static RAMDirectory dir;
    private static LuxSearcher searcher;
    private static Saxon eval;
    private static int numtests;
    private static int numfailed;
    
    @BeforeClass
    public static void setup () throws Exception {
        dir = new RAMDirectory();
        eval = new Saxon();
        // This indexer does nothing, the effect of which is to disable Lux search optimizations for
        // absolute expressions depending on the context. This makes it possible to evaluate tests that
        // make use of the dynamic context.  Thus we're only really testing the Translator here, and not
        // the Optimizer or Query components of Lux.
        XmlIndexer indexer = new XmlIndexer (0);
        eval.getConfig().setErrorListener(new ErrorIgnorer ());
        catalog = new Catalog ("/users/sokolov/workspace/XQTS_1_0_3", eval.getProcessor());
        indexDirectory (indexer, catalog);
        searcher = new LuxSearcher(dir);
        eval.setContext(new SaxonContext(searcher, indexer));
        numtests = 0;
    }
    
    private static void indexDirectory(XmlIndexer indexer, Catalog catalog2) throws IOException {
        IndexWriter indexWriter = indexer.getIndexWriter(dir);
/*
        File dir = new File(catalog.getDirectory() + "/TestSources");
        int count = 0;
        System.out.println ("indexing test sources...");        
        for (File source : dir.listFiles()) {
            if (! source.getName().endsWith(".xml")) {
                // skip the dtds and schemas and xquery files
                continue;
            }
            try {
                indexer.indexDocument (indexWriter, source.getName(), new FileInputStream(source));
            } catch (XMLStreamException e) {
                System.err.println ("Failed to index " + source.getName() + ": " + e.getMessage());
            }
            ++count;
        }
        System.out.println ("indexed " + count + " documents");
*/
        indexWriter.commit();
        indexWriter.close();
    }

    @AfterClass 
    public static void cleanup () throws Exception {
        searcher.close();
        dir.close();
        System.out.println ("Ran " + numtests + " tests");
        System.out.println (numfailed + " tests failed");
    }

    private boolean runTest (String caseName) throws Exception {
        TestCase test1 = catalog.getTestCaseByName(caseName);
        assertNotNull (caseName, test1);
        return runTest (test1);
    }
    
    private boolean runTest (TestCase test1) throws Exception {
        ++numtests;
        try {
            SaxonExpr expr = (SaxonExpr) eval.compile(test1.getQueryText());
            //System.out.println (expr);
            QueryStats stats = new QueryStats();
            eval.setQueryStats(stats);
            ResultSet<?> results = (ResultSet<?>) eval.evaluate(expr, test1.getContextItem());
            Boolean comparedEqual = test1.compareResult (results);            
            if (comparedEqual == null || comparedEqual) {
                //System.out.println (test1.getName() + " OK in " + stats.totalTime + "ms");
                return true;
            } else {
                System.err.println (test1.getName() + " Mismatch: " + TestCase.resultToString(results) + " is not " + test1.getOutputText()[0]);
                ++numfailed;
                XdmItem item = eval.getProcessor().newXQueryCompiler().compile(test1.getQueryText()).load().evaluateSingle();
                if (! test1.compareResult(item)) {
                    System.err.println (test1.getName() + " Saxon fails too?");
                }
                return false;
            }
        } catch (Exception e) {
            // TODO: compare errors against expected errors
            String scenario = test1.getScenario();
            if (! ("runtime-error".equals(scenario) ||
                    "parse-error".equals(scenario) ||
                    test1.getComparisonMode() == ComparisonMode.Ignore)) {
                System.err.println (test1.getName() + " at " + test1.getPath() + " Unexpected Error: " + e.getMessage());
                //throw (e);                
                ++numfailed;
                return false;                
            }
            //System.out.println (test1.getName() + " OK (expected error)");
            return true;
        }
    }

    private void runTestGroup (String groupName) throws Exception {
        TestGroup group = catalog.getTestGroupByName(groupName);
        assertNotNull (groupName, group);
        testOneGroup (group);
    }

    private void testOneGroup (TestGroup group) throws Exception {
        System.out.println(group.getBannerString());
        for (TestCase test : group.getTestCases()) {
            runTest (test);
        }
        for (TestGroup subgroup : group.getSubGroups()) {
            testOneGroup (subgroup);
        }
    }
    
    @Test public void testOne () throws Exception {
        assertTrue (runTest ("op-date-greater-than2args-1"));
    }

    @Test public void testLiterals054 () throws Exception {
        assertTrue (runTest ("Literals054"));
    }
    
    @Test public void testLiterals056 () throws Exception {
        assertTrue (runTest ("Literals056"));
    }
    
    @Test public void testLiterals066 () throws Exception {
        assertTrue (runTest ("Literals066"));
    }
    
    @Test public void testLiterals005 () throws Exception {
        assertTrue (runTest ("Literals005"));
    }
    
    @Test public void testLiterals004 () throws Exception {
        assertTrue (runTest ("Literals004"));
    }
    
    @Test public void testLiteralsK2_4 () throws Exception {
        assertTrue (runTest ("K2-Literals-4"));
    }
    
    @Test public void testLiteralsK2_8 () throws Exception {
        assertTrue (runTest ("K2-Literals-8"));
    }

    @Test public void testPathExpr6() throws Exception {
        assertTrue (runTest ("PathExpr-6"));
    }

    @Test public void testAxes091() throws Exception {
        assertTrue (runTest ("Axes091"));
    }
    
    @Test public void testParenExpr11() throws Exception {
        assertTrue (runTest ("Parenexpr-11"));
    }
    
    @Test public void testParenExpr20() throws Exception {
        assertTrue (runTest ("Parenexpr-20"));
    }
    
    @Test public void testExternalContextItem2() throws Exception {
        assertTrue (runTest ("externalcontextitem-2"));
    }
    
    @Test public void testExternalContextItem9() throws Exception {
        assertTrue (runTest ("externalcontextitem-9"));
    }

    @Test public void testExternalContextItem22() throws Exception {
        assertTrue (runTest ("externalcontextitem-22"));
    }

    @Test public void testK2FunctionCallExpr10() throws Exception {
        assertTrue (runTest ("K2-FunctionCallExpr-10"));
    }

    @Test public void testK2FunctionCallExpr11() throws Exception {
        assertTrue (runTest ("K2-FunctionCallExpr-11"));
    }

    @Test public void testK2Steps12() throws Exception {
        assertTrue (runTest ("K2-Steps-12"));
    }
    
    @Test public void testK2Steps15() throws Exception {
        assertTrue (runTest ("K2-Steps-15"));
    }
    
    @Test public void testK2Steps20() throws Exception {
        assertTrue (runTest ("K2-Steps-20"));
    }
    
    @Test public void testK2Steps35() throws Exception {
        assertTrue (runTest ("K2-Steps-35"));
    }

    @Test public void testAxes036_2() throws Exception {
        assertTrue (runTest ("Axes036-2"));
    }
    

    @Test public void testStepsLeadingLoneSlash8a() throws Exception {
        assertTrue (runTest ("Steps-leading-lone-slash-8a"));
    }

    @Test public void testStepsLeadingLoneSlash1a() throws Exception {
        // fails since we don't implement "instance of"
        assertTrue (runTest ("Steps-leading-lone-slash-1a"));
    }
    
    @Test public void testGroup () throws Exception {
        runTestGroup ("Basics");
        runTestGroup ("Expressions");
    }
    
    static class ErrorIgnorer implements ErrorListener {

        public void warning(TransformerException exception) throws TransformerException {
        }

        public void error(TransformerException exception) throws TransformerException {
        }

        public void fatalError(TransformerException exception) throws TransformerException {
        }
        
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
