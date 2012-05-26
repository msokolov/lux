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

import net.sf.saxon.s9api.XQueryExecutable;
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
    private boolean terminateOnException = true;
    private boolean printDetailedDiagnostics = false;
    
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
        if (printDetailedDiagnostics) {
            ErrorIgnorer ignorer = (ErrorIgnorer) eval.getConfig().getErrorListener();
            ignorer.setShowErrors(true);
        }
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
                // debugging diagnostics:
                if (printDetailedDiagnostics) {
                    XQueryExecutable xq = eval.getProcessor().newXQueryCompiler().compile(test1.getQueryText());
                    XdmItem item = xq.load().evaluateSingle();
                    if (! test1.compareResult(item)) {
                        System.err.println (test1.getName() + " Saxon fails too?");
                    } else {
                        System.err.println (eval.getTranslator().queryFor(xq));
                    }
                }
                return false;
            }
        } catch (Exception e) {
            // Saxon's XQTS report says it doesn't check actual error codes, so neither do we
            if (! (test1.isExpectError() ||
                    test1.getComparisonMode() == ComparisonMode.Ignore)) {                
                System.err.println (test1.getName() + " at " + test1.getPath() + " Unexpected Error: " + e.getMessage());
                // diagnostics:
                if (printDetailedDiagnostics ) {
                    XQueryExecutable xq = eval.getProcessor().newXQueryCompiler().compile(test1.getQueryText());
                    XdmItem item = xq.load().evaluateSingle();
                    System.err.println (test1.getQueryText() + " returns " + item);
                }
                if (terminateOnException) {
                    throw (e); 
                } else {
                    ++numfailed;
                    return false;
                }
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
    
    @Test public void testNameTest68() throws Exception {
        // fails because declared type match is not enforced
        assertTrue (runTest ("K2-NameTest-68"));
    }
    
    @Test public void testOneTest() throws Exception {
        printDetailedDiagnostics = true;
        assertTrue (runTest ("Constr-cont-eol-3"));
    }
    
    @Test public void testGroup () throws Exception {
        terminateOnException = false;
        runTestGroup ("Basics");
        runTestGroup ("Expressions");
    }
    
    static class ErrorIgnorer implements ErrorListener {
        
        private boolean showErrors = false;
        
        public void warning(TransformerException exception) throws TransformerException {
            if (showErrors) {
                System.err.println (exception.getMessageAndLocation());
            }
        }

        public void setShowErrors(boolean b) {
            this.showErrors = b;    
        }

        public void error(TransformerException exception) throws TransformerException {
            if (showErrors) {
                System.err.println (exception.getMessageAndLocation());
            }
        }

        public void fatalError(TransformerException exception) throws TransformerException {
            if (showErrors) {
                System.err.println (exception.getMessageAndLocation());
            }
        }
        
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
