package lux.xqts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;

import lux.Compiler;
import lux.Compiler.SearchStrategy;
import lux.QueryContext;
import lux.QueryStats;
import lux.XdmResultSet;
import lux.compiler.PathOptimizer;
import lux.compiler.SaxonTranslator;
import lux.exception.LuxException;
import lux.index.IndexConfiguration;
import lux.xml.QName;
import lux.xqts.TestCase.ComparisonMode;
import lux.xquery.XQuery;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;

import org.junit.BeforeClass;
import org.junit.Test;

public class TestRunner extends RunnerBase {

    private static final int MIL = 1000000;
    private long compile0;
    private long compile1;
    private long compileTime;
    private long translateTime;
    private long optimizeTime;
    private long evalTime;
    private long bindTime;
    private String luxQuery;
    
    @BeforeClass
    public static void setup() throws Exception {
        // By default, no indexes are created, and no Lux query optimizations are performed.
        // This has the effect of testing the query translator only
        setup(IndexConfiguration.STORE_DOCUMENT, "TestSources");
        // setup(IndexConfiguration.DEFAULT_OPTIONS, "TestSources");
    }

    private boolean runTest (String caseName) throws Exception {
        TestCase test1 = catalog.getTestCaseByName(caseName);
        assertNotNull ("test case not found in catalog: " + caseName, test1);
        return runTest (test1);
    }
    
    private boolean runTest (TestCase test1) throws Exception {
        ++numtests;
        QueryContext context = new QueryContext();
        try {
            long t00 = System.nanoTime();
            bindExternalVariables(test1, context);
            long t0 = System.nanoTime();
            XQueryExecutable expr = compileXQuery(test1.getQueryText());
            context.setContextItem(test1.getContextItem());
            if (printDetailedDiagnostics) {
                eval.setQueryStats(new QueryStats());
            }
            long t1 = System.nanoTime();
            XdmResultSet results = (XdmResultSet) eval.evaluate(expr, context);
            long t2 = System.nanoTime();
            bindTime += (t0 - t00);
            // accumulated inside compileXQuery
            // compileTime += (t1 - t0);
            evalTime += (t2 - t1);
            if (benchmark) {
                return true;
            }
            if (!results.getErrors().isEmpty()) {
                throw results.getErrors().get(0);
            }
            /*
            if (test1.isExpectError()) {
                System.err.println (test1.getName() + " did not cause expected error");
                return false;
            }
            */
            Boolean comparedEqual = test1.compareResult (results);
            if (comparedEqual == null || comparedEqual) {
                //System.out.println (test1.getName() + " OK in " + stats.totalTime + "ms");
                return true;
            } else {
                System.err.println (test1.getName() + " Mismatch: " + TestCase.resultToString(results) + " is not " + test1.getOutputText()[0]);
                ++numfailed;
                // debugging diagnostics:
                if (printDetailedDiagnostics) {
                    SearchStrategy currentStrategy = eval.getCompiler().getSearchStrategy();
                    eval.getCompiler().setSearchStrategy(SearchStrategy.NONE);
                    System.err.print (test1.getQueryText());
                    XQueryExecutable xq = eval.getCompiler().getXQueryCompiler().compile(test1.getQueryText());
                    results = (XdmResultSet) eval.evaluate(xq, context);
                    XdmItem item = results.getXdmValue().itemAt(0);
                    if (! test1.compareResult(item)) {
                        System.err.println (test1.getName() + " Saxon fails too?");
                    } else {
                        System.err.println (new SaxonTranslator(saxonConfig).queryFor(xq));
                    }
                    eval.getCompiler().setSearchStrategy(currentStrategy);
                }
                return false;
            }
        } catch (Exception e) {
            // Saxon's XQTS report says it doesn't check actual error codes, so neither do we
            if (! (test1.isExpectError() || test1.getComparisonMode() == ComparisonMode.Ignore)) { 
                ++numfailed;
                String error = e.getMessage();
                if (error != null && error.length() > 1024) {
                    error = error.substring(0, 1024);
                }
                System.err.println (test1.getName() + " at " + test1.getPath() + " Unexpected Error: " + error);
                // diagnostics:
                if (printDetailedDiagnostics ) {
                    XQueryExecutable xq = eval.getCompiler().getXQueryCompiler().compile(test1.getQueryText());
                    XQueryEvaluator xqeval = xq.load();
                    if (context.getVariableBindings() != null) {
                        for (Map.Entry<QName, Object> binding : context.getVariableBindings().entrySet()) {
                            net.sf.saxon.s9api.QName saxonQName = new net.sf.saxon.s9api.QName(binding.getKey());
                            xqeval.setExternalVariable(saxonQName, (XdmValue) binding.getValue());
                        }
                    }
                    System.err.print("translated query: " + luxQuery + "\n\n");
                    try {
                        XdmItem item = xqeval.evaluateSingle();
                        System.err.println (test1.getQueryText() + " returns " + item);
                    } catch (Exception e1) {
                        System.err.println (test1.getQueryText() + "\n\n failed while trying to print diagnostics");
                        e1.printStackTrace();
                    }
                }
                if (terminateOnException) { 
                    throw (e); 
                } else {
                    return false;
                }
            }
            //e.printStackTrace();
            //System.out.println (test1.getName() + " OK (expected error)");
            return true;
        }
    }
    
    // break out some internals of the lux optimizer so we can measure timings
    private XQueryExecutable compileXQuery(String exprString) throws LuxException {
        XQueryExecutable xquery;
        long t0 = System.nanoTime();
        try {
            xquery = eval.getCompiler().getXQueryCompiler().compile(new StringReader(exprString));
        } catch (SaxonApiException e) {
            throw new LuxException (e);
        } catch (IOException e) {
            throw new LuxException (e);
        }
        long t1 = System.nanoTime();
        compile0 += (t1 - t0);
        compileTime += (t1 - t0);
        XQuery abstractQuery = eval.getCompiler().makeTranslator().queryFor (xquery);
        long t2 = System.nanoTime();
        translateTime += (t2 - t1);
        if (eval.getCompiler().getSearchStrategy() != SearchStrategy.NONE) {
            PathOptimizer optimizer = new PathOptimizer(eval.getCompiler().getIndexConfiguration());
            XQuery optimizedQuery = optimizer.optimize(abstractQuery);
            luxQuery = optimizedQuery.toString();
        } else {
            luxQuery = abstractQuery.toString();
        }
        long t3 = System.nanoTime();
        optimizeTime += (t3 - t2);
        try {
            xquery = eval.getCompiler().getXQueryCompiler().compile(new StringReader(luxQuery));
        } catch (SaxonApiException e) {
            System.err.print("Error compiling " + luxQuery + "\n");
            throw new LuxException (e);
        } catch (IOException e) {
            throw new LuxException (e);
        }
        float compileTimeRatio = (t3 - t2) / (float) (t1 - t0);
        if (t3 - t2 > 2 * (t1 - t0)) {
            System.out.println ("Query got " + compileTimeRatio + " times harder to compile after optimization");
        }
        long t4 = System.nanoTime();
        compileTime += (t4 - t3);
        compile1 += (t4 - t3);
        return xquery;
    }


    private void runTestGroup (String groupName) throws Exception {
        ErrorIgnorer ignorer = (ErrorIgnorer) saxonConfig.getErrorListener();
        if (printDetailedDiagnostics) {
            ignorer.setShowErrors(true);
        } else {
            ignorer.setShowErrors(false);
        }
        TestGroup group = catalog.getTestGroupByName(groupName);
        assertNotNull (groupName, group);
        testOneGroup (group);
    }

    private void testOneGroup (TestGroup group) throws Exception {
        //System.out.println(group.getBannerString());
        for (TestCase test : group.getTestCases()) {
            runTest (test);
        }
        for (TestGroup subgroup : group.getSubGroups()) {
            testOneGroup (subgroup);
        }
    }
    
    @Test public void testOneTest() throws Exception {
        printDetailedDiagnostics = true;
        // Constr-cont-nsmode-11 requires schema-aware processing
        
        // K2-DirectConElemContent-35 Mismatch: true is not false 
        // K2-ComputeConElem-9 Mismatch: true is not false
        // do these require schema-awarenes???
        // assertTrue (runTest ("K2-DirectConElemContent-35"));  
        
        // These two I don't understand what's wrong with the generated expression - maybe a Saxon bug?
        // K2-sequenceExprTypeswitch-14: The context item for axis step self::node() is undefined 
        // K2-ExternalVariablesWithout-11: The context item is absent, so position() is undefined
        // K2-SeqExprCast-209 Mismatch: 〜 is not &#12316; // count as pass
        //<e/>/(typeswitch (self::node())
        //        case $i as node() return .
        // becomes:
        //        declare namespace zz="http://saxon.sf.net/";
        //        (<e >{() }</e>)/(let $zz:zz_typeswitchVar := self::node() return if ($zz:zz_typeswitchVar instance of node()) then (.) else (1))
        //assertTrue (runTest ("K2-sequenceExprTypeswitch-14"));
        // assertTrue (runTest ("fn-id-dtd-5"));
        //assertTrue (runTest ("fn-collection-4"));
        
        // These tests fail when we add the SaxonTranslator into the mix
        // we are dropping the types from name tests:
        // assertTrue (runTest ("K2-NameTest-68"));
        
        // I have no idea why this is failing?
        assertTrue (runTest ("K2-sequenceExprTypeswitch-14"));
        //
        // and this as well:
        // assertTrue (runTest ("K2-ExternalVariablesWithout-11"));
        //
        // if subsequence($b,$p,1) below is changed to $b[$p] then the expression compiles,
        // otherwise you get an error about no context item being defined
        //
        // declare variable $a as attribute()* := ((attribute { "name1" } { "" }),(attribute { "name2" } { "" }),(attribute { "name3" } { "" }));
        // declare variable $b as attribute()* := ((attribute { "name1" } { "" }),(attribute { "name2" } { "" }),(attribute { "name3" } { "" }));
        // ($a)/(let $p := position() return . is subsequence($b,$p,1))
        // assertTrue (runTest ("TypedArguments-1"));
        // assertTrue (runTest ("TypedArguments-2"));
    }
    
    @Test public void testGroup () throws Exception {
        terminateOnException = false;
        // 2012-12-26: 19/19426 tests fail - 5 are surrogate-related; 10 are collection-related
        //             22/17497 fail w/lux translation and no optimization
        //             61/17498 fail w/lux optimization - we have changed the default context item
        runTestGroup ("MinimalConformance"); 
        //runTestGroup ("FunctX");
        //runTestGroup ("Basics");
        //runTestGroup ("Expressions");
    }
    
    /*
     * Run test cases through Saxon only so we can compare runtime with and without Lux optimization
     * runtime with Saxon alone: 34102
     * runtime including Lux optimizing compilation: 53170
     */
    @Test public void testBenchmarkCompiler () throws Exception {
        benchmarkComparison(5, "MinimalConformance");
    }
    
    private void benchmarkComparison (int runs, String testGroup) throws Exception {
        terminateOnException = false;
        benchmark = true;
        Compiler compiler = eval.getCompiler();
        compiler.setSearchStrategy(SearchStrategy.LUX_SEARCH);
        runTestGroup (testGroup);
        compiler.setSearchStrategy(SearchStrategy.NONE);
        runTestGroup (testGroup);
        System.out.println ("Benchmark Saxon alone");
        benchmark(runs, testGroup);
        System.out.println ("Benchmark including Lux optimize");
        compiler.setSearchStrategy(SearchStrategy.LUX_SEARCH);
        benchmark(runs, testGroup);
    }

    private void benchmark(int runs, String testGroup) throws Exception {
        evalTime = 0;
        compileTime = 0;
        compile0 = 0;
        compile1 = 0;
        bindTime = 0;
        optimizeTime = 0;
        translateTime = 0;
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < runs; i++) {
            runTestGroup (testGroup);
        }
        long t1 = System.currentTimeMillis();
        System.out.println (String.format("total time=%dms, bind=%dms, compile=%dms, translate=%dms, optimize=%dms, eval=%dms\n", 
                (t1-t0), bindTime/MIL, compileTime/MIL, translateTime/MIL, optimizeTime/MIL, evalTime/MIL));
        System.out.println (String.format("compile0=%d, compile1=%d\n", compile0/MIL, compile1/MIL));
    }
    
    /*
     * Run test cases, replacing the context item with collection().
     * Compare results and timing using Lux with results and timing using Saxon
     * alone fetching files from the file system.
     * 
     * To do this:
     * 1. Change the processing of context items when we load the tests to bind external variables to collection()
     * 2. Change the test runner so it compares results from Lux and Saxon (not from XQTS)
     * 2a. the test runner should skip tests that throw errors, and those that use the emptydoc as context
     * 3. Change setup() so it actually indexes the content and optimizes the queries
     * 
     * We also need to update Lux so that it optimizes (specifically) the use of collection() via a variable.
     */
    @Test public void testBenchmark () throws Exception {
        terminateOnException = false;
        benchmark = true;
        eval.getCompiler().setSearchStrategy(SearchStrategy.LUX_SEARCH);
        //benchmarkComparison (100, "Basics");
        benchmarkComparison (50, "PathExpr");
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
