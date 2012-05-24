package lux;

import static org.junit.Assert.assertEquals;

import java.util.Iterator;

import lux.api.ResultSet;
import lux.api.ValueType;
import lux.index.XmlIndexer;
import lux.saxon.Saxon;
import lux.saxon.SaxonExpr;
import lux.saxon.UnOptimizer;
import lux.xpath.AbstractExpression;
import net.sf.saxon.s9api.XdmItem;

import org.junit.AfterClass;
import org.junit.BeforeClass;

public class SearchTest2 extends BasicQueryTest {
        
    @Override
    public String getQueryString(Q q) {
        switch (q) {
        case ACT_SCENE: return "w(\"ACT\",\"SCENE\")";
        case SCENE: return "\"SCENE\"";
        default: throw new UnsupportedOperationException("No query string for " + q + " in " + getClass().getSimpleName());
        }
    }

    @Override
    public XmlIndexer getIndexer() {
        return new XmlIndexer (XmlIndexer.INDEX_PATHS);
    }

    public void assertQuery (String xpath, String optimized, int facts, ValueType type, Q ... queries) {
        Saxon saxon = SearchBase.getEvaluator();
        SaxonExpr saxonExpr = saxon.compile(xpath);
        ResultSet<?> results = saxon.evaluate(saxonExpr);
        //System.out.println ("query evaluated in " + (System.currentTimeMillis() - t) + " msec,  retrieved " + results.size() + " result");
        AbstractExpression aex = saxon.getTranslator().exprFor(saxonExpr.getSaxonInternalExpression());
        aex = new UnOptimizer(getIndexer().getOptions()).unoptimize(aex);
        SaxonExpr baseline = saxon.compile(aex.toString());
        ResultSet<?> baseResult = saxon.evaluate(baseline);
        assertEquals ("result count mismatch for: " + saxonExpr.toString(), baseResult.size(), results.size());        
        Iterator<?> baseIter = baseResult.iterator();
        Iterator<?> resultIter = results.iterator();
        for (int i = 0 ; i < results.size(); i++) {
            XdmItem base = (XdmItem) baseIter.next();
            XdmItem r = (XdmItem) resultIter.next();
            assertEquals (base.isAtomicValue(), r.isAtomicValue());
            assertEquals (base.getStringValue(), r.getStringValue());
        }
        // TODO: also assert facts about query optimizations
    }
    
    @BeforeClass
    public static void setUp() throws Exception {
        SearchBase.setUp();
    }
    
    @AfterClass
    public static void tearDown() throws Exception {
        SearchBase.tearDown();
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
