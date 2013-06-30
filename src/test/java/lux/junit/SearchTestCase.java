package lux.junit;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import lux.Compiler.SearchStrategy;
import lux.Evaluator;
import lux.QueryStats;
import lux.XdmResultSet;
import lux.exception.LuxException;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.store.LockObtainFailedException;

public class SearchTestCase extends QueryTestCase {

    private static long elapsed=0;
    private static long elapsedBaseline=0;
    protected static int repeatCount=1;
    private static ArrayList<TestTime> baseTimes, testTimes;
    
	SearchTestCase(String name, String query, QueryTestResult expected) {
		super(name, query, expected);
		if (repeatCount > 1) {
			// for benchmarking; not currently working
			baseTimes = new ArrayList<TestTime>();
			testTimes = new ArrayList<TestTime>();
		}
	}
	
	public void evaluate (Evaluator eval, Evaluator baselineEval, int repeatCount) {
        baselineEval.getCompiler().setSearchStrategy(SearchStrategy.LUX_UNOPTIMIZED);
        if (repeatCount > 1) {
            try {
				benchmark (eval, baselineEval);
			} catch (IOException e) {
				fail (e.getMessage());
			}
        } else {
            XdmResultSet results = evaluate (eval);
            XdmValue baseResult = evaluate(baselineEval).getXdmValue();
            assertEquals ("result count mismatch for: " + getQuery(), baseResult.size(), results.size());        
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
	
	public XdmResultSet evaluate(Evaluator eval) {
		if (eval.getDocReader() != null) {
			eval.getDocReader().clear();
		}
        eval.setQueryStats(new QueryStats());
        XdmResultSet results = null;
    	String expectedError = getExpectedResult().errorText;
        try {
        	results = eval.evaluate(getQuery());
        } catch (LuxException e) {
        	if (! getExpectedResult().isError) { 
        		fail (e.getMessage());
        	}
        	if (expectedError != null) {
        		assertEquals (expectedError, e.getMessage());
        	}
        	return null;
        }
        if (!results.getErrors().isEmpty()) {
        	if (! getExpectedResult().isError) { 
        		fail (results.getErrors().iterator().next().getMessageAndLocation());
        	}
        	if (expectedError != null) {
        		assertEquals (expectedError, results.getErrors().iterator().next().getMessage());
        	}
        } else {
        	if (getExpectedResult().isError) {
        		fail ("expected error did not occur");
        	}        	
        }
        return results;
    }
	
	private void benchmark (Evaluator baselineEval, Evaluator testEval) throws CorruptIndexException, LockObtainFailedException, IOException {
        XdmResultSet results = evaluate(testEval);
        XdmValue baselineResult = evaluate(baselineEval).getXdmValue();
        TestTime baseTime = new TestTime("baseline", getQuery(), repeatCount);
        baseTimes.add(baseTime);
        TestTime testTime = new TestTime("indexed", getQuery(), repeatCount);
        testTimes.add(testTime);
        for (int i = 0; i < repeatCount; i++) {
            
            testEval.getDocReader().clear(); // no fair caching!
            long t0 = System.nanoTime();
            evaluate(testEval);
            long t = System.nanoTime() - t0;
            testTime.times[i] = t;
            elapsed += t;
            
            baselineEval.getDocReader().clear(); // no fair caching!
            t0 = System.nanoTime();
            evaluate(baselineEval);
            t = System.nanoTime() - t0;
            baseTime.times[i] = t;
            elapsedBaseline += t;
        }
        System.out.println (String.format("%dms using lux; %dms w/o lux", elapsed/1000000, elapsedBaseline/1000000));
        
        results = evaluate(testEval);
        System.out.println ("lux retrieved " + results.size() + " results from " + testEval.getQueryStats());
        printDocReaderStats(testEval);
        baselineResult = evaluate(baselineEval).getXdmValue();
        System.out.println ("baseline (no lux): retrieved " + baselineResult.size() + " results from " + baselineEval.getQueryStats());
        printDocReaderStats(baselineEval);
    }

    private void printDocReaderStats(Evaluator saxon) {
        System.out.println (String.format(" %d/%d cache hits/misses, %dms building docs", 
                saxon.getDocReader().getCacheHits(), saxon.getDocReader().getCacheMisses(),
                saxon.getDocReader().getBuildTime()/1000000));
    }
}
