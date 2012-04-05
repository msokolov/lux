package lux.saxon;

import static org.junit.Assert.assertEquals;

import java.util.List;

import lux.SearchTest;
import lux.XPathQuery;
import lux.api.ResultSet;

import org.apache.lucene.search.MatchAllDocsQuery;
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
        SaxonExpr baseline = new SaxonExpr (saxonExpr.getXPathExecutable(), 
                new XPathQuery (null, new MatchAllDocsQuery(), 0, saxonExpr.getXPathQuery().getResultType()));
        ResultSet<?> baseResult = saxon.evaluate(baseline);
        assertEquals ("result count mismatch when filtered by query: " + saxonExpr.getSearchQuery(), baseResult.size(), results.size());        
    }
    
    @Test
    public void testConstantExpression() throws Exception {
        // This resolves to a constant (Literal=true()) XPath expression and generates
        // a null Lucene query.  Make sure we don't try to execute the query.
        List<?> results = assertSearch("'remorseless' or descendant::text", QUERY_EXACT);
        assertEquals (1, results.size());
    }

}
