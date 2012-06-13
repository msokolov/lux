package lux.xqts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import lux.api.QueryContext;
import lux.api.QueryStats;
import lux.api.ResultSet;
import lux.index.XmlIndexer;
import lux.saxon.SaxonExpr;
import lux.xqts.TestCase.ComparisonMode;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.CollectionURIResolver;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.ArrayIterator;
import net.sf.saxon.value.AnyURIValue;

import org.junit.BeforeClass;
import org.junit.Test;

public class BenchmarkRunner extends RunnerBase {
    
    @BeforeClass
    public static void setup() throws Exception {
        // Create path indexes and optimize queries to use them
        setup(XmlIndexer.INDEX_PATHS | XmlIndexer.STORE_XML, "TestSourcesTiny");
    }

    private boolean runTest (String caseName) throws Exception {
        TestCase test1 = catalog.getTestCaseByName(caseName);
        assertNotNull (caseName, test1);
        return runComparison (test1);
    }

    private void runSaxonBenchmark (TestCase test1) throws Exception {
        ++numtests;
        QueryContext context = new QueryContext();
        bindExternalVariables(test1, context);
        // TODO: later try TestSourcesClean which includes some larger XML files
        eval.getConfig().setCollectionURIResolver(new BenchmarkURIResolver());
        eval.getConfig().setDefaultCollection(catalog.getDirectory() + "/TestSourcesTiny");
        XQueryExecutable xq = eval.getProcessor().newXQueryCompiler().compile(test1.getBenchmarkQueryText());
        xq.load().evaluate();
    }
    
    private void runLuxBenchmark (TestCase test1) throws Exception {
        ++numtests;
        QueryContext context = new QueryContext();
        bindExternalVariables(test1, context);
        eval.getConfig().setCollectionURIResolver(eval);
        eval.getConfig().setDefaultCollection("lux:/");
        SaxonExpr expr = (SaxonExpr) eval.compile(test1.getBenchmarkQueryText());
        context.setContextItem(test1.getContextItem());
        QueryStats stats = new QueryStats();
        eval.setQueryStats(stats);
        eval.evaluate(expr, context);
    }
    
    private boolean runComparison (TestCase test1) throws Exception {
        ++numtests;
        if (printDetailedDiagnostics) {
            ErrorIgnorer ignorer = (ErrorIgnorer) eval.getConfig().getErrorListener();
            ignorer.setShowErrors(true);
        }
        QueryContext context = new QueryContext();
        bindExternalVariables(test1, context);

        eval.getConfig().setDefaultCollection(catalog.getDirectory() + "/TestSourcesTiny");
        eval.getConfig().setCollectionURIResolver(new BenchmarkURIResolver());
        boolean threwException = false;
        XQueryExecutable xq = null;
        XdmValue value = null;
        try {
            xq = eval.getProcessor().newXQueryCompiler().compile(test1.getBenchmarkQueryText());
            value= xq.load().evaluate();
        } catch (Exception e) {
            threwException = true;
        }
        eval.getConfig().setDefaultCollection("lux:/");
        eval.getConfig().setCollectionURIResolver(eval);
        SaxonExpr expr = (SaxonExpr) eval.compile(test1.getBenchmarkQueryText());
        context.setContextItem(test1.getContextItem());
        //System.out.println (expr);
        QueryStats stats = new QueryStats();
        eval.setQueryStats(stats);
        ResultSet<?> results = (ResultSet<?>) eval.evaluate(expr, context);
        if (results.getException() != null) {
            if (! threwException) {
                ++numfailed;
                System.err.println ("test failed with unexpected exception: "+ results.getException());
            }
            // both base and optimized processes threw exceptions
            return true;
        }        
        Boolean comparedEqual = test1.compareResult (results, value);
        if (comparedEqual == null || comparedEqual) {
            return true;
        } else {
            ++numfailed;
            System.err.println (test1.getName() + " failed");
            // debugging diagnostics:
            if (printDetailedDiagnostics) {
                System.err.println ("base impl returned: " + value.toString());
                System.err.println ("lux impl returned: " + results.iterator().next().toString());
                System.err.println (eval.getTranslator().queryFor(xq));
            }
            return false;
        }
    }

    private void runTestGroup (String groupName) throws Exception {
        TestGroup group = catalog.getTestGroupByName(groupName);
        assertNotNull (groupName, group);
        testOneGroup (group);
    }

    private void benchmarkLuxGroup (TestGroup group) throws Exception {
        //System.out.println(group.getBannerString());
        for (TestCase test : group.getTestCases()) {
            if (! (test.isExpectError() || test.getComparisonMode() == ComparisonMode.Ignore) &&
                    !("emptydoc".equals(test.getPrincipalData()))) { 
                try {
                    runLuxBenchmark(test);
                }
                catch (Exception e) {
                    ++numfailed;
                    System.err.println ("lux benchmark test " + test.getName() + " failed with exception: " + e.getMessage());
                }
            }
        }
        for (TestGroup subgroup : group.getSubGroups()) {
            benchmarkLuxGroup (subgroup);
        }
    }
    
    private void benchmarkSaxonGroup (TestGroup group) throws Exception {
        //System.out.println(group.getBannerString());
        for (TestCase test : group.getTestCases()) {
            if (! (test.isExpectError() || test.getComparisonMode() == ComparisonMode.Ignore) &&
                    !("emptydoc".equals(test.getPrincipalData()))) { 
                try {
                    runSaxonBenchmark(test);
                } catch (Exception e) {
                    ++numfailed;
                    System.err.println ("base benchmark test " + test.getName() + " failed with exception: " + e.getMessage());
                }
            }
        }
        for (TestGroup subgroup : group.getSubGroups()) {
            benchmarkSaxonGroup(subgroup);
        }
    }

    private void testOneGroup (TestGroup group) throws Exception {
        //System.out.println(group.getBannerString());
        for (TestCase test : group.getTestCases()) {
            if (! (test.isExpectError() || test.getComparisonMode() == ComparisonMode.Ignore) &&
                    !("emptydoc".equals(test.getPrincipalData()))) {
                boolean ok = true;
                try {
                    ok = runComparison(test);
                } catch (Exception e) {
                    // System.err.println (test.getName() + " failed with exception: " + e.getMessage());
                }
                assertTrue (test.getName() + " fails", ok);
            }
        }
        for (TestGroup subgroup : group.getSubGroups()) {
            testOneGroup (subgroup);
        }
    }
    
    @Test public void testOneBenchmark() throws Exception {
        printDetailedDiagnostics = true;
        assertTrue (runTest ("Parenexpr-15"));
    }
    
    @Test public void testGroup() throws Exception {
        // eliminating all the tests using emptydoc as their context leaves only 1281 tests, of which 173 fail
        // to get matching results
        runTestGroup ("MinimalConformance");
    }    

    /*
     * Run test cases, replacing the context item with collection().
     * Compare results and timing using Lux with results and timing using Saxon
     * alone fetching files from the file system.
     * 
     * To do this, we need to:
     * 1. Change the processing of context items when we load the tests to bind external variables to collection()
     * 2. Change the test runner so it compares results from Lux and Saxon (not from XQTS)
     * 2a. the test runner should skip tests that throw errors, and those that use the emptydoc as context
     * 3. Change setup() so it actually indexes the content and optimizes the queries
     * 
     * We also need to update Lux so that it optimizes (specifically) the use of collection() via a variable.
     */
    
    @Test public void testBenchmarkGroup () throws Exception {
        terminateOnException = false;
        long t0 = System.currentTimeMillis();
        benchmarkSaxonGroup(catalog.getTestGroupByName("MinimalConformance"));
        long t1 = System.currentTimeMillis();
        benchmarkLuxGroup(catalog.getTestGroupByName("MinimalConformance"));
        long t2 = System.currentTimeMillis();
        int optDocsRead = eval.getDocReader().getCacheMisses();;
        System.out.println ("Base runtime (all documents): " + (t1-t0) + "; read " + (collectionSize * numtests) + " docs");
        System.out.println ("Optimized runtime (documents filtered by query): " + (t2-t1) + "; read " + optDocsRead + " docs");
        //runTestGroup ("FunctX");
        //runTestGroup ("Basics");
        //runTestGroup ("Expressions");
    }
    
    static class BenchmarkURIResolver implements CollectionURIResolver {

        /**
         * read a directory and return a list of uris; this is the way to get Saxon to maintain
         * a document cache so that collection() is stable
         */
        public SequenceIterator<?> resolve(String href, String base, XPathContext context) throws XPathException {
            File dir = new File(href);
            String[] listing = dir.list();
            Item<?> [] uris = new Item[listing.length];
            for (int i = 0; i < listing.length; i++) {
                uris[i] = new AnyURIValue(href + '/' + listing[i]);
            }
            return new ArrayIterator<Item<?>>(uris);
        }
        
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
