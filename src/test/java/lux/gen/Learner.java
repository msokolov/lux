package lux.gen;

import static org.junit.Assert.*;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Random;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;

import org.apache.commons.chain.web.MapEntry;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.junit.BeforeClass;
import org.junit.Test;

import lux.SearchBase;
import lux.SearchTest;
import lux.api.Evaluator;
import lux.api.LuxException;
import lux.saxon.Saxon;
import lux.saxon.SaxonContext;

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
    
    @Test 
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
    
    @Test 
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
    
    @Test 
    public void testEvalGenerated () {
        // evaluate all of a first query generation; this will determine the Lucene queryFor each,
        // but evaluates against a single document instance.
        Saxon saxon = new Saxon();
        XdmNode hamlet = (XdmNode) saxon.getBuilder().build(new InputStreamReader (SearchTest.class.getClassLoader().getResourceAsStream("lux/hamlet.xml")));
        saxon.setContext(new SaxonContext(searcher, hamlet));
        ExprGen gen = new ExprGen(termCounts.keySet().toArray(new String[0]), elementCounts.keySet().toArray(new String[0]), new Random(), 2, 2);
        gen.setSaxon (saxon);
        ExprBreeder breeder = new ExprBreeder(gen);
        int count = 0;
        int errors = 0;
        int docs = 0;
        int results = 0;
        int empties = 0;
        for (Expression expr : breeder) {
            // filter out expressions that don't depend on anything; they are constant, effectively
            if (expr.computeDependencies() == 0)
                continue;
            ++count;
            try {
                XdmValue result = (XdmValue) saxon.evaluate(saxon.compile(expr.toString()));
                if (result.size() > 0) {
                    docs ++;
                    results += result.size();
                } else {
                    empties ++;
                }
            } catch (LuxException e) {
                System.err.println (e.getMessage());
                ++ errors;
            }
        }
        System.out.println ("evaluated " + count + " expressions");
        System.out.println (empties + " of those returned no results");
        System.out.println ("generated " + errors + " errors");
        System.out.println ("matched " + docs + " documents");
        System.out.println ("retrieved " + results + " results");
        assertEquals (count, docs + empties + errors);
    }

    @Override
    public Evaluator getEvaluator() {        
        Evaluator eval = new Saxon();
        eval.setContext(new SaxonContext(searcher));
        return eval;
    }

}
