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

// TODO: merge w/SearchTest, inherit from SearchBase, not BasicQueryTest:
// pull out all the test cases (xquery plus expected results)
// into a separate catalog: a class or data structure
public class SearchTest2 extends BasicQueryTest {
        
    @Override
    public void populateQueryStrings() {
        // unused
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
        AbstractExpression aex = saxon.getTranslator().exprFor(saxonExpr.getXPathExecutable().getUnderlyingExpression().getInternalExpression());
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
