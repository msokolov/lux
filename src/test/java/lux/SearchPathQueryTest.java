package lux;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Iterator;

import lux.exception.LuxException;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.saxon.Expandifier;
import lux.xml.ValueType;
import lux.xquery.XQuery;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;

import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.store.LockObtainFailedException;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * executes all the BasicQueryTest test cases using path indexes and compares results against
 * an unindexed/unoptimized baseline.
 *
 */
public class SearchPathQueryTest extends BasicQueryTest {
    private static IndexTestSupport index;
    private static long elapsed=0;
    private static long elapsedBaseline=0;
    private static int repeatCount=1;
    
    @BeforeClass
    public static void setupClass () throws Exception {
        index = new IndexTestSupport();
    }
    
    @AfterClass
    public static void tearDownClass() throws Exception {
        index.close();
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
        return new XmlIndexer (IndexConfiguration.INDEX_PATHS);
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
    public void assertQuery (String xpath, int facts, ValueType type, Q ... queries) throws IOException {
        if (repeatCount > 1) {
            benchmark (xpath);
        } else {
            Evaluator saxon = index.makeEvaluator();
            XdmResultSet results = evalQuery(xpath, saxon);
            XdmValue baseResult = evalBaseline(xpath, saxon);
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

    private void benchmark (String query) throws CorruptIndexException, LockObtainFailedException, IOException {
        Evaluator eval2 = index.makeEvaluator();
        XdmResultSet results = evalQuery(query, eval2);
        XdmValue baselineResult = evalBaseline(query, eval2);
        for (int i = 0; i < repeatCount; i++) {
            long t0 = System.nanoTime();
            evalQuery(query, eval2);
            long t = System.nanoTime() - t0;
            elapsed += t;
            t0 = System.nanoTime();
            evalBaseline(query, eval2);
            t = System.nanoTime() - t0;
            elapsedBaseline += t;
        }
        System.out.println (String.format("%dms using lux; %dms w/o lux", elapsed/1000000, elapsedBaseline/1000000));
        
        results = evalQuery(query, eval2);
        System.out.println ("lux retrieved " + results.size() + " results from " + eval2.getQueryStats());
        printDocReaderStats(eval2);
        baselineResult = evalBaseline(query, eval2);
        System.out.println ("baseline (no lux): retrieved " + baselineResult.size() + " results from " + eval2.getQueryStats());
        printDocReaderStats(eval2);
    }

    private void printDocReaderStats(Evaluator saxon) {
        System.out.println (String.format(" %d/%d cache hits/misses, %dms building docs", 
                saxon.getDocReader().getCacheHits(), saxon.getDocReader().getCacheMisses(),
                saxon.getDocReader().getBuildTime()/1000000));
    }

    private XdmValue evalBaseline(String xpath, Evaluator eval2) {
        XdmValue baseResult;
        XQuery xq = null;
        Compiler compiler2 = eval2.getCompiler();
        try {
            xq = compiler2.makeTranslator().queryFor(compiler2.compile(xpath));
            xq = new Expandifier().expandify(xq);
            String expanded = xq.toString();
            XQueryExecutable baseline;
            baseline = compiler2.compile(expanded, eval2.getErrorListener());
            XQueryEvaluator baselineEval = baseline.load();
            baselineEval.setErrorListener(eval2.getErrorListener());
            baseResult = baselineEval.evaluate();
        } catch (SaxonApiException e) {
            throw new LuxException ("error evaluting query " + xq, e);
        }
        return baseResult;
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

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
