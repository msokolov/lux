package lux.saxon;

import static org.junit.Assert.assertEquals;
import lux.Compiler;
import lux.Evaluator;
import lux.compiler.PathOptimizer;
import lux.compiler.SaxonTranslator;
import lux.exception.LuxException;
import lux.xquery.XQuery;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XQueryExecutable;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

// Note: A number of these tests now fail since we now have added some alternate serialization for
// elements.  The test was useful during development, but too brittle to really maintain long term.
// We should consider abandoning this now that we have the XQTS test suite working since it really
// covers the same ground in a much more robust way.
public class TranslatorTest {
    
    private Evaluator eval;
    
    @Before public void setup () {
        eval = new Evaluator();
    }
    
    @Test @Ignore public void testTranslate () {
        roundtrip ("/");
        roundtrip (".");
        roundtrip ("foo");
        roundtrip ("//foo/bar");
        roundtrip ("//foo[ancestor::bar]");        
        roundtrip ("concat('a','b')");
        roundtrip ("descendant::foo[1]");
        roundtrip ("descendant::foo[2]");
        roundtrip ("descendant::foo[last()]");
        roundtrip ("false()");
        // saxon applies an interesting optimization to this one:
        // count(//bar) becomes count(subsequence(//bar,1,2))
        roundtrip ("(1 eq count(//bar)) and ../foo");
        roundtrip ("count(//bar) eq 10");
    }
    
    @Test @Ignore public void testSetOperations() {
        roundtrip ("node() except comment()");
        roundtrip ("node() union comment()");
        roundtrip ("//a//b intersect //c//b");
        roundtrip ("a except b/a");
        roundtrip ("(a | b | c) except b");
    }
    
    @Test @Ignore public void testTypeCasts () {
        roundtrip ("//a[xs:integer(@x) gt 2] intersect //a[@y='dog']");
    }

    @Test @Ignore public void testBooleanOperations() {
        roundtrip ("node() or ../foo");
        roundtrip ("node() and self::foo");
        roundtrip ("true() or false()");
        roundtrip ("true() and false()");
        roundtrip ("not(node() or ../foo)");
        roundtrip ("not(node() and self::foo)");
        roundtrip ("not(node()) or ../foo");
        roundtrip ("not(node()) and self::foo");
        roundtrip ("true() or false()");
        roundtrip ("true() and false()");
    }

    // test fails due to funkiness with AtomicSequenceConverter the queries
    // seem equivalent, but we end up with data() in one of them?
    @Test @Ignore public void testMathOperations () {
        roundtrip ("x + y");
        roundtrip ("x - y");
        roundtrip ("x * y");
        roundtrip ("x div y");
        roundtrip ("x idiv y");
        roundtrip ("x mod y");
        roundtrip ("count(x) + count(y)");
        roundtrip ("-x");
        roundtrip ("+x");
    }
    
    @Test public void testAtomicComparisons () {
        roundtrip ("x eq y");
        roundtrip ("x ne y");
        roundtrip ("x gt y");
        roundtrip ("x lt y");
        roundtrip ("x ge y");
        roundtrip ("x le y");
    }
    
    @Test public void testGeneralComparisons () {
        roundtrip ("x = y");
        roundtrip ("x != y");
        roundtrip ("x > y");
        roundtrip ("x < y");
        roundtrip ("x >= y");
        roundtrip ("x <= y");
    }
    
    @Test public void testIdentityComparisons () {
        roundtrip ("x is y");
        roundtrip ("x << y");
        roundtrip ("x >> y");
    }
    
    @Test public void testDocumentNodeTest () {
        roundtrip ("document-node()");
        roundtrip ("foo | document-node(element(bar))");
    }
    
    @Test public void testNodeTest () {

        roundtrip ("a|b");
        roundtrip (".//*[self::a or self::b]");
        roundtrip ("text()");
        roundtrip (".//text()");
        roundtrip ("processing-instruction()");
        roundtrip ("processing-instruction('process')");
    }
    
    @Test @Ignore public void testLetExpr () {
        // Saxon takes this one out into xquery land, representing as a let $x := () return xxx($x)??
        roundtrip ("(a,b,c)[//foo/@count + 1]");
        roundtrip ("preceding::*[count(//*)]");
        roundtrip ("(preceding::*)[count(//*)]");
        roundtrip ("*[count(//*)]");
        roundtrip ("(*)[count(//*)]");
    }
    
    @Test public void testSequence() {
        roundtrip ("()");
        roundtrip ("(a,b,c,d/e)");
        roundtrip ("(1,3,'a',14.6,true())");
    }
    
    @Test @Ignore public void testForwardAxes() {
        roundtrip ("*");
        roundtrip ("./*");
        roundtrip ("../*");
        roundtrip (".//*");
        roundtrip ("//*");
        roundtrip ("following::*");
        roundtrip ("following-sibling::*");
        roundtrip ("self::*");
        roundtrip ("attribute::*");
    }
    
    @Test @Ignore public void testReverseAxes () {
        roundtrip ("ancestor::*");
        roundtrip ("reverse(ancestor::*)");
        roundtrip ("ancestor-or-self::*");
        roundtrip ("./preceding::element()");
        roundtrip ("preceding::*");
        roundtrip ("preceding-sibling::*");
        roundtrip ("//x/ancestor::*");
        roundtrip ("../../x");
    }
    
    @Test public void testPositionalPredicates () {
        roundtrip ("preceding::*[1]");
        roundtrip ("(preceding::*)[1]");
        roundtrip ("*[1]");
        roundtrip ("(*)[1]");
        roundtrip ("descendant::*[3]");
        roundtrip ("(descendant::*)[3]");
        roundtrip ("ancestor::*[3]");
        roundtrip ("(ancestor::*)[3]");
        roundtrip ("descendant-or-self::*[last()]");
        roundtrip ("(descendant-or-self::*)[last()]");
        roundtrip ("ancestor-or-self::*[last()]");
        roundtrip ("(ancestor-or-self::*)[last()]");
    }
    
    private void roundtrip (String xpath) {
        XQueryExecutable query;
        Compiler compiler = eval.getCompiler();
        try {
            query = compiler.getXQueryCompiler().compile(xpath);
        } catch (SaxonApiException e) {
            throw new LuxException(e);
        }
        SaxonTranslator translator = new SaxonTranslator(compiler.getProcessor().getUnderlyingConfiguration());
        PathOptimizer optimizer = new PathOptimizer(compiler.getIndexConfiguration());
        XQuery originalQuery = translator.queryFor(query);
        XQuery optimizedQuery = optimizer.optimize(originalQuery);
        XQuery retranslated;
        try {
            retranslated = translator.queryFor(compiler.getXQueryCompiler().compile(optimizedQuery.getBody().toString()));
        } catch (SaxonApiException e) {
            throw new LuxException (e);
        }
        // compare the (compiled, translated, serialized) optimized query with the 
        // (compiled, translated, serialized) original query
        assertEquals (xpath + " was not preserved", 
                originalQuery.getBody().toString(), 
                retranslated.getBody().toString());
    }
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
