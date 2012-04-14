package lux.saxon;

import static org.junit.Assert.assertEquals;

import java.util.List;

import lux.SearchTest;
import lux.api.ResultSet;
import lux.xpath.AbstractExpression;

import org.junit.Test;

public class SaxonSearchTest extends SearchTest {

    @Override
    public Saxon getEvaluator() {
        Saxon eval = new Saxon();
        eval.setContext(new SaxonContext(searcher));
        return eval;
    }
    
    @Test
    public void testTextComparison () {
        // This fails because our baseline checks the condition for each document, and for each document,
        // the expression returns either true(), or false(), so the number of results = the number of documents
        // Our 'optimized' query retrieves just those documents (2 of them) containing SCNDESCR, and returns true()
        // (or maybe false?) for each of them, yielding 2 results.
        //
        // What we should be doing is considering each sequence as spanning the entire collection of documents
        // and returning a single result (true()).
        //
            //  See discussion in doc/NOTES
        
        String xpath = "descendant::element(SCNDESCR) >= descendant::text()";
        Saxon saxon = getEvaluator();
        SaxonExpr saxonExpr = saxon.compile(xpath);
        ResultSet<?> results = saxon.evaluate(saxonExpr);
        AbstractExpression aex = saxon.getTranslator().exprFor(saxonExpr.getXPathExecutable().getUnderlyingExpression().getInternalExpression());
        aex = new UnOptimizer().unoptimize(aex);
        SaxonExpr baseline = saxon.compile(aex.toString());
        ResultSet<?> baseResult = saxon.evaluate(baseline);
        assertEquals ("result count mismatch for: " + saxonExpr.toString(), baseResult.size(), results.size());        
    }
    
    @Test
    public void testConstantExpression() throws Exception {
        // This resolves to a constant (Literal=true()) XPath expression and generates
        // a null Lucene query.  Make sure we don't try to execute the query.
        List<?> results = assertSearch("'remorseless' or descendant::text", QUERY_EXACT);
        assertEquals (1, results.size());
    }
    
    // A test case that exposes the difference between evaluating the entire expression
    // for each document, and evaluating the sub-expressions independently, 
    // for each document, which is correct since they both have collection()-wide scope
    @Test
    public void testCollectionScope() throws Exception {
        List<?> results = assertSearch("count (//PERSONA[.='ROSENCRANTZ']) + count(//PERSONA[.='GUILDENSTERN'])", 0);
        assertEquals (1, results.size());
        assertEquals (60, results.get(0));        
    }
    
}
