package lux;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import lux.exception.LuxException;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.xml.ValueType;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.RAMDirectory;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * executes all the BasicQueryTest test cases using path indexes and compares results against
 * an unindexed/unoptimized baseline.
 *
 */
public class SearchPathQueryTest extends BasicQueryTest {
    private static IndexTestSupport index;
    protected static XmlIndexer indexer;
    protected static XmlIndexer baselineIndexer;
    private static long elapsed=0;
    private static long elapsedBaseline=0;
    private static int repeatCount=5;
    private static ArrayList<TestTime> baseTimes, testTimes;
    
    @BeforeClass
    public static void setupClass () throws Exception {
        indexer = new XmlIndexer(IndexConfiguration.DEFAULT_OPTIONS);
        baselineIndexer = new XmlIndexer(0);
        index = new IndexTestSupport("lux/hamlet.xml", indexer, new RAMDirectory());
        baseTimes = new ArrayList<SearchPathQueryTest.TestTime>();
        testTimes = new ArrayList<SearchPathQueryTest.TestTime>();
    }
    
    @AfterClass
    public static void tearDownClass() throws Exception {
        index.close();
        printAllTimes();
    }
    
    private static void printAllTimes() {
        int n = baseTimes.size();
        System.out.println(String.format("query\t%s\t%s\t%%change", baseTimes.get(0).condition, testTimes.get(0).condition));
        for (int i = 0; i < n; i++) {
            //System.out.println (baseTimes.get(i));
            //System.out.println (testTimes.get(i));
            System.out.println (testTimes.get(i).comparison(baseTimes.get(i)));
        }
    }
    
    @Override
    public String getQueryString(Q q) {
        switch (q) {
        case ACT_SCENE: return "w(\"ACT\",\"SCENE\")";
        case SCENE: return "\"SCENE\"";
        default: return super.getQueryString(q);
        }
    }

    @Override
    public String getQueryXml (Q q) {
        switch (q) {
        case ACT_SCENE: return "<SpanNear inOrder=\"true\" slop=\"0\">" +
        		"<SpanTerm fieldName=\"lux_path\">ACT</SpanTerm>" +
        		"<SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm>" +
        		"</SpanNear>";
        case SCENE: return "<SpanTerm fieldName=\"lux_path\">SCENE</SpanTerm>";
        default: return super.getQueryXml(q);
        }
    }

    @Override
    public XmlIndexer getIndexer() {
        return indexer;
    }

    /**
     * @param xpath the path to test
     * @param facts ignored
     * @param queries ignored
     * @throws IOException 
     * @throws LockObtainFailedException 
     * @throws CorruptIndexException 
     */
    @Override
    public void assertQuery (String xpath, Integer facts, ValueType type, Q ... queries) throws IOException {
        Evaluator testEval = null;
        try {
            testEval = new Evaluator(new Compiler (indexer.getConfiguration()), index.searcher, null);
        } catch (Exception e) {
            e.printStackTrace();
            fail (e.toString());
        }
        Evaluator baselineEval = new Evaluator(new Compiler (baselineIndexer.getConfiguration()), index.searcher, null);
        if (repeatCount > 1) {
            benchmark (xpath, baselineEval, testEval);
        } else {
            //Evaluator eval = index.makeEvaluator();
            XdmResultSet results = evalQuery(xpath, testEval);
            XdmValue baseResult = evalQuery(xpath, baselineEval).getXdmValue();
            assertEquals ("result count mismatch for: " + xpath, baseResult.size(), results.size());        
            Iterator<?> baseIter = baseResult.iterator();
            Iterator<?> resultIter = results.iterator();
            for (int i = 0 ; i < results.size(); i++) {
                XdmItem base = (XdmItem) baseIter.next();
                XdmItem r = (XdmItem) resultIter.next();
                assertEquals (base.isAtomicValue(), r.isAtomicValue());
                assertEquals (base.getStringValue(), r.getStringValue());
            }
        }
    }

    private void benchmark (String query, Evaluator baselineEval, Evaluator testEval) throws CorruptIndexException, LockObtainFailedException, IOException {
        XdmResultSet results = evalQuery(query, testEval);
        XdmValue baselineResult = evalQuery(query, baselineEval).getXdmValue();
        TestTime baseTime = new TestTime("baseline", query, repeatCount);
        baseTimes.add(baseTime);
        TestTime testTime = new TestTime("indexed", query, repeatCount);
        testTimes.add(testTime);
        for (int i = 0; i < repeatCount; i++) {
            long t0 = System.nanoTime();
            evalQuery(query, testEval);
            long t = System.nanoTime() - t0;
            testTime.times[i] = t;
            elapsed += t;
            t0 = System.nanoTime();
            evalQuery(query, baselineEval);
            t = System.nanoTime() - t0;
            baseTime.times[i] = t;
            elapsedBaseline += t;
        }
        System.out.println (String.format("%dms using lux; %dms w/o lux", elapsed/1000000, elapsedBaseline/1000000));
        
        results = evalQuery(query, testEval);
        System.out.println ("lux retrieved " + results.size() + " results from " + eval.getQueryStats());
        printDocReaderStats(testEval);
        baselineResult = evalQuery(query, baselineEval).getXdmValue();
        System.out.println ("baseline (no lux): retrieved " + baselineResult.size() + " results from " + baselineEval.getQueryStats());
        printDocReaderStats(baselineEval);
    }

    private void printDocReaderStats(Evaluator saxon) {
        System.out.println (String.format(" %d/%d cache hits/misses, %dms building docs", 
                saxon.getDocReader().getCacheHits(), saxon.getDocReader().getCacheMisses(),
                saxon.getDocReader().getBuildTime()/1000000));
    }

    private XdmResultSet evalQuery(String xpath, Evaluator eval2) {
        eval2.getDocReader().clear();
        eval2.setQueryStats(new QueryStats());
        XQueryExecutable xquery = eval2.getCompiler().compile(xpath);
        XdmResultSet results = eval2.evaluate(xquery);
        if (!results.getErrors().isEmpty()) {
            throw new LuxException(results.getErrors().iterator().next());
        }
        return results;
    }
    
    @Override
    protected boolean hasPathIndexes () {
        return true;
    }
    
    private static class TestTime {
        TestTime (String condition, String query, int n) {
            this.condition = condition;
            this.query = query;
            this.times = new long[n];
        }
        
        String condition;
        String query;
        long [] times;
        
        long meanTime() {
            long total = 0;
            for (long t : times) {
                total += t;
            }
            return total / times.length;
        }
        
        @Override public String toString () {
            return String.format ("%s %s %d", condition, query, meanTime()/1000000);
        }
        
        public String comparison (TestTime other) {
            return String.format ("%s\t%d\t%d\t%.2f", query, 
                    other.meanTime()/1000000, meanTime()/1000000, 
                    100 * ((other.meanTime() - meanTime()) / ((double)other.meanTime())));
        }
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
