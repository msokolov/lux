package lux;

import static org.junit.Assert.assertEquals;

import java.util.Iterator;

import lux.api.LuxException;
import lux.api.QueryStats;
import lux.api.ResultSet;
import lux.api.ValueType;
import lux.index.XmlIndexer;
import lux.saxon.Expandifier;
import lux.saxon.Saxon;
import lux.saxon.SaxonExpr;
import lux.xquery.XQuery;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;

import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * executes all the BasicQueryTest test cases using path indexes and compares results against
 * an unindexed baseline.
 *
 */
public class SearchQueryTest extends BasicQueryTest {
    private static IndexTestSupport index;
    private static long elapsed=0;
    private static long elapsedBaseline=0;
    
    @BeforeClass
    public static void setup () throws Exception {
        index = new IndexTestSupport();
    }
    
    @AfterClass
    public static void tearDown() throws Exception {
        index.close();
    }
    
    @Override
    public String getQueryString(Q q) {
        switch (q) {
        case ACT_SCENE: return "w(\"ACT\",\"SCENE\")";
        case SCENE: return "\"SCENE\"";
        default: throw new UnsupportedOperationException("No query string for " + q + " in " + getClass().getSimpleName());
        }
    }

    @Override
    public String getQueryXml (Q q) {
        switch (q) {
        case ACT_SCENE: return "<SpanNear ordered=\"true\" slop=\"1\">" +
        		"<SpanTerm>ACT</SpanTerm>" +
        		"<SpanTerm>SCENE</SpanTerm>" +
        		"</SpanNear>";
        case SCENE: return "SCENE";
        default: throw new UnsupportedOperationException("No query string for " + q + " in " + getClass().getSimpleName());
        }
    }

    @Override
    public XmlIndexer getIndexer() {
        return new XmlIndexer (XmlIndexer.INDEX_PATHS);
    }

    /**
     * @param xpath the path to test
     * @param optimized ignored
     * @param facts ignored
     * @param queries ignored
     */
    public void assertQuery (String xpath, String optimized, int facts, ValueType type, Q ... queries) {
        Saxon saxon = index.getEvaluator();
        saxon.invalidateCache();
        long t0 = System.nanoTime();
        SaxonExpr saxonExpr = saxon.compile(xpath);
        ResultSet<?> results = saxon.evaluate(saxonExpr);
        if (results.getException() != null) {
            throw new LuxException(results.getException());
        }
        long t = System.nanoTime() - t0;
        elapsed += t;
        System.out.println ("query evaluated in " + (t/1000000) + " msec,  retrieved " + results.size() + " results from " +
                saxon.getQueryStats().docCount + " documents");
        saxon.invalidateCache();
        saxon.setQueryStats(new QueryStats());
        t0 = System.nanoTime();
        XQuery xq = null;
        try {
            xq = saxon.getTranslator().queryFor(saxon.getXQueryCompiler().compile(xpath));
            xq = new Expandifier().expandify(xq);
            String expanded = xq.toString();
            XQueryExecutable baseline;
            baseline = saxon.getXQueryCompiler().compile(expanded);
            XQueryEvaluator baselineEval = baseline.load();
            XdmValue baseResult = baselineEval.evaluate();
            assertEquals ("result count mismatch for: " + optimized, baseResult.size(), results.size());        
            Iterator<?> baseIter = baseResult.iterator();
            Iterator<?> resultIter = results.iterator();
            for (int i = 0 ; i < results.size(); i++) {
                XdmItem base = (XdmItem) baseIter.next();
                XdmItem r = (XdmItem) resultIter.next();
                assertEquals (base.isAtomicValue(), r.isAtomicValue());
                assertEquals (base.getStringValue(), r.getStringValue());
            }
        } catch (SaxonApiException e) {
            throw new LuxException ("error evaluting query " + xq, e);
        }
        t = System.nanoTime() - t0;
        System.out.println ("baseline query took " + (t/1000000) + " msec,  retrieved " + results.size() + " results from " +
                saxon.getQueryStats().docCount + " documents");
        elapsedBaseline += t;
        // TODO: also assert facts about query optimizations
        System.out.println (String.format("%dms using lux; %dms w/o lux", elapsed/1000000, elapsedBaseline/1000000));
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
