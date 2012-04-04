package lux.gen;

import static org.junit.Assert.assertEquals;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Random;

import lux.SearchBase;
import lux.SearchTest;
import lux.XPathQuery;
import lux.api.Evaluator;
import lux.api.LuxException;
import lux.api.ResultSet;
import lux.api.ValueType;
import lux.saxon.Saxon;
import lux.saxon.SaxonContext;
import lux.saxon.SaxonExpr;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.s9api.XdmNode;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Learns discriminative XPath expressions 
 */
public class Learner extends SearchBase {
    
    protected static HashMap<String,Integer> termCounts = new HashMap<String,Integer>();
    protected static int totalTermFreq;
    
    @BeforeClass
    public static void setUp() throws Exception {
        SearchBase.setUp();
        // enumerate all the terms in the index, in all fields, and record their frequencies
        // also get the total term frequency, for normalization
        TermEnum terms = searcher.getIndexReader().terms();        
        totalTermFreq = 0; 
        while (terms.next()) {
            int freq = terms.docFreq();
            Term term = terms.term();
            if (term.field().equals("xml_text_only")) {
                termCounts.put(term.text(), freq);
                totalTermFreq += freq;
            }
        } 
        terms.close(); 
    }
    
    @Test @Ignore
    public void testTermCounts () {
        System.out.println ("total docs=" + totalDocs);
        System.out.println ("total distinct terms=" + termCounts.size());
        System.out.println ("total term count=" + totalTermFreq);
        /*
        for (Entry<String, Integer> kv : termCounts.entrySet()) {
            System.out.println (kv.getKey() + ": " + kv.getValue());
        }
        */
    }
    
    @Test @Ignore
    public void testGenerateQuery () {
        ExprGen gen = new ExprGen(termCounts.keySet().toArray(new String[0]), elementCounts.keySet().toArray(new String[0]), new Random(), 2, 2);
        ExprBreeder breeder = new ExprBreeder(gen);
        int count = 0;
        for (Expression expr : breeder) {
            if (expr.computeDependencies() == 0)
                continue;
            ++count;
            if (count % 100 == 0)
                System.out.println (expr);
            
            // filter out expressions that don't depend on the context item - ditto
            // this should include constant expressions - only primitives can be constant
        }
        // with breadth = 5, generates 16924 - excluding constant expressions leaves 13499
        // with breadth = 2, generates 4135 - without constants: 3587
        System.out.println ("generated " + count + " expressions");
    }
    
    @Test @Ignore
    public void testEvalGenerated () {
        // evaluate all of a first query generation; this will determine the Lucene queryFor each,
        // but evaluates against a single document instance.
        Saxon saxon = new Saxon();
        XdmNode hamlet = (XdmNode) saxon.getBuilder().build(new InputStreamReader (SearchTest.class.getClassLoader().getResourceAsStream("lux/hamlet.xml")));
        saxon.setContext(new SaxonContext(searcher, hamlet));
        evalGenerated (saxon, false);
    }
    
    @Test @Ignore
    public void testSearchGenerated () {
        // evaluate all of a first query generation, running queries against Lucene.
        Saxon saxon = new Saxon();
        saxon.setContext(new SaxonContext(searcher));        
        evalGenerated (saxon, true);
    }
    
    private void evalGenerated (Saxon saxon, boolean validateResults) {
        ExprGen gen = new ExprGen(termCounts.keySet().toArray(new String[0]), elementCounts.keySet().toArray(new String[0]), 
                    new Random(), saxon, 2, 2);
        ExprBreeder breeder = new ExprBreeder(gen);
        int count = 0;
        int compilationErrors = 0;
        int runtimeErrors = 0;
        int docs = 0;
        int nonempty = 0;
        int results = 0;
        int empties = 0;
        int singletons = 0;
        int minimal_correct = 0;
        int minimal_incorrect = 0;
        for (Expression expr : breeder) {
            // filter out expressions that don't depend on anything; they are constant, effectively
            if (expr.computeDependencies() == 0)
                // TODO: optimize this when evaluating
                continue;
            ++count;
            String exprString = expr.toString().replaceAll("lastItem(.*)","($1)[last()]");
            SaxonExpr saxonExpr=null;
            try {
                saxonExpr = saxon.compile(exprString);
            } catch (LuxException e) {
                System.err.println (e.getMessage());
                ++ compilationErrors;
                continue;
            }
            try {
                ResultSet<?> result = saxon.evaluate(saxonExpr);
                boolean isDocQuery = saxonExpr.getXPathQuery().getResultType().equals(ValueType.DOCUMENT);
                int ndocs = 0;
                int nresults = result.size();
                if (nresults > 0) {
                    ++nonempty;
                    if (saxon.getQueryStats() != null) {
                        ndocs = saxon.getQueryStats().docCount;
                    } else {
                        ndocs = 1;
                    }
                    results += nresults;
                    if (result.size() == 1) {
                        singletons ++;
                        if (saxonExpr.getXPathQuery().isMinimal() && isDocQuery) {
                            System.out.println ("minimal: " + expr);                            
                            ++minimal_correct;
                        }
                    } else {
                        if (saxonExpr.getXPathQuery().isMinimal() && isDocQuery) {
                            ++minimal_incorrect;
                            System.out.println ("not minimal: " + expr);
                        }
                    }
                } else {
                    empties ++;
                }
                System.out.println (ndocs + " " + nresults + " " + expr);
                docs += ndocs;
                if (validateResults) {
                    SaxonExpr baseline = new SaxonExpr (saxonExpr.getXPathExecutable(), 
                            new XPathQuery (null, new MatchAllDocsQuery(), 0, saxonExpr.getXPathQuery().getResultType()));
                    result = saxon.evaluate(baseline);
                    assertEquals ("result count mismatch when filtered by query: " + saxonExpr.getSearchQuery(), result.size(), nresults);
                }
            } catch (LuxException e) {
                //System.err.println (e.getMessage() + " in " + expr);
                ++ runtimeErrors;
            }
        }
        System.out.println ("evaluated " + count + " expressions");
        System.out.println (empties + " of those returned no results");
        System.out.println ("generated " + compilationErrors + " compilation errors");
        System.out.println ("generated " + runtimeErrors + " runtime errors");
        System.out.println ("matched " + docs + " documents");
        System.out.println ("retrieved " + results + " results");
        System.out.println (singletons + " had a single result");
        System.out.println ("of those, " + minimal_correct+ " were predicted");
        System.out.println ("there were " + minimal_incorrect + " incorrect predictions of single-result queries");
        assertEquals (count, nonempty + empties + compilationErrors + runtimeErrors);
    }

    @Override
    public Evaluator getEvaluator() {        
        Evaluator eval = new Saxon();
        eval.setContext(new SaxonContext(searcher));
        return eval;
    }

}
