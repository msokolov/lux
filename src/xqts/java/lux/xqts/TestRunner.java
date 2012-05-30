package lux.xqts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;

import lux.api.QueryContext;
import lux.api.QueryStats;
import lux.api.ResultSet;
import lux.index.XmlIndexer;
import lux.lucene.LuxSearcher;
import lux.saxon.Saxon;
import lux.saxon.SaxonExpr;
import lux.xpath.QName;
import lux.xqts.TestCase.ComparisonMode;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmItem;

import org.apache.commons.io.IOUtils;
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
    private static int numignored;
    private boolean terminateOnException = true;
    private boolean printDetailedDiagnostics = false;
    
    @BeforeClass
    public static void setup () throws Exception {
        eval = new Saxon(null, null);
        dir = new RAMDirectory();
        // This indexer does nothing, the effect of which is to disable Lux search optimizations for
        // absolute expressions depending on the context. This makes it possible to evaluate tests that
        // make use of the dynamic context.  Thus we're only really testing the Translator here, and not
        // the Optimizer or Query components of Lux.
        XmlIndexer indexer = new XmlIndexer (0);
        catalog = new Catalog ("/users/sokolov/workspace/XQTS_1_0_3", eval.getProcessor());
        indexDirectory (indexer, catalog);
        searcher = new LuxSearcher(dir);
        eval.setIndexer (indexer);
        eval.setSearcher(searcher);
        eval.getConfig().setErrorListener(new ErrorIgnorer ());
        eval.getConfig().setConfigurationProperty(FeatureKeys.XQUERY_PRESERVE_NAMESPACES, true);
        numtests = 0;
        numignored = 0;
        numfailed = 0;
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
        //searcher.close();
        //dir.close();
        System.out.println ("Ran " + numtests + " tests");
        System.out.println (numfailed + " tests failed; " + numignored + " ignored");
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
            QueryContext context = new QueryContext();
            if (test1.getExternalVariables() != null) {
                for (Map.Entry<String,String> binding : test1.getExternalVariables().entrySet()) {
                    String filename = binding.getValue();
                    String text = IOUtils.toString (new FileInputStream(filename));
                    SaxonExpr expr = (SaxonExpr) eval.compile(text);
                    XdmItem item = (XdmItem) expr.evaluate(null).iterator().next();
                    context.bindVariable(new QName(binding.getKey()), item);
                }
            }
            SaxonExpr expr = (SaxonExpr) eval.compile(test1.getQueryText());
            context.setContextItem(test1.getContextItem());
            //System.out.println (expr);
            QueryStats stats = new QueryStats();
            eval.setQueryStats(stats);
            ResultSet<?> results = (ResultSet<?>) eval.evaluate(expr, context);
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
            if (! (test1.isExpectError() || test1.getComparisonMode() == ComparisonMode.Ignore)) { 
                ++numfailed;
                String error = e.getMessage();
                if (error.length() > 1024) {
                    error = error.substring(0, 1024);
                }
                System.err.println (test1.getName() + " at " + test1.getPath() + " Unexpected Error: " + error);
                // diagnostics:
                if (printDetailedDiagnostics ) {
                    XQueryExecutable xq = eval.getProcessor().newXQueryCompiler().compile(test1.getQueryText());
                    XdmItem item = xq.load().evaluateSingle();
                    System.err.println (test1.getQueryText() + " returns " + item);
                }
                if (terminateOnException) {
                    throw (e); 
                } else {
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
    
    @Test public void testOneTest() throws Exception {
        printDetailedDiagnostics = true;
        //assertTrue (runTest ("extvardeclwithouttype-1"));
        //assertTrue (runTest ("functx-fn-root-1"));
        assertTrue (runTest ("copynamespace-4"));
        //assertTrue (runTest ("op-add-yearMonthDuration-to-dateTime-1"));
    }
    
    @Test public void testGroup () throws Exception {
        terminateOnException = false;
        //runTestGroup ("MinimalConformance");
        //runTestGroup ("FunctX");
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
