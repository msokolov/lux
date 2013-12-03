package lux.xpath;

import static org.junit.Assert.*;
import lux.Compiler;
import lux.compiler.SaxonTranslator;
import lux.index.IndexConfiguration;

import net.sf.saxon.s9api.SaxonApiException;

import org.junit.Before;
import org.junit.Test;

public class TestEquals {
    private Compiler compiler;
    private SaxonTranslator translator;

    @Before
    public void startup() {
        compiler = new Compiler (new IndexConfiguration());
        translator = new SaxonTranslator (compiler.getProcessor().getUnderlyingConfiguration());
    }

    @Test
    public void testEquivalent () throws Exception {
        // It's no longer possible to create a PathStep this way; as of saxon 9.5 this compiles
        // to a path: ./a
        // assertEquivalent ("a", "a");
        // assertNotEquivalent ("c", "a");
        assertEquivalent ("/a/b/c", "/a/b/c");
        assertEquivalent ("/a/b/c", "/x");
        assertEquivalent ("xs:double(1)", "xs:double(1.0)");
        assertNotEquivalent ("xs:double(1)", "1");
        assertEquivalent ("current-dateTime()", "current-dateTime()");
        assertNotEquivalent ("current-dateTime()", "not(true())");
        
        // operators
        assertEquivalent ("a=b", "c=d");
        assertNotEquivalent ("a=b", "a!=b");

        // functions
        assertEquivalent ("concat(a,b)", "concat(b,c)");
        assertNotEquivalent ("concat(a,b)", "substring-after(a,b)");
        assertNotEquivalent ("string(a)", "a/string()");
        
        // predicates
        assertEquivalent ("a[b]", "a[b]");
        assertEquivalent ("a[b]", "b[a]"); // all we do is check that this is a predicate
        
        // sequence, subsequence
        assertEquivalent ("(0,1,2)", "('x','y','z')");
        assertEquivalent ("(0,1,2)[1]", "subsequence((0,1,2),1,1)");
        assertEquivalent ("subsequence((0,1,2),1,3)", "subsequence((0,1,2),1,3)");
        assertEquivalent ("subsequence((0,1,2),x,y)", "subsequence((0,1,2),a,b)");
    }
    
    private void assertEquivalent (String xp1, String xp2) throws Exception {
        AbstractExpression cx1 = compile(xp1);
        AbstractExpression cx2 = compile(xp2);
        assertTrue (xp1 + " != " + xp2, cx1.equivalent(cx2));
        assertTrue (xp2 + " != " + xp1, cx2.equivalent(cx1));
    }

    private void assertNotEquivalent (String xp1, String xp2) throws Exception {
        AbstractExpression cx1 = compile(xp1);
        AbstractExpression cx2 = compile(xp2);
        assertTrue (xp1 + " = " + xp2, ! cx1.equivalent(cx2));
        assertTrue (xp2 + " = " + xp1, ! cx2.equivalent(cx1));
    }

    @Test
    public void testDeepEquals () throws Exception {
        assertDeepEquals ("a", "a");
        assertNotDeepEquals ("c", "a");
        assertDeepEquals ("/a/b/c", "/a/b/c");
        assertNotDeepEquals ("/a/b/c", "/x");
        assertDeepEquals ("xs:double(1)", "xs:double(1.0)");
        assertNotDeepEquals ("xs:double(1)", "1");
        assertDeepEquals ("current-dateTime()", "current-dateTime()");
        assertNotDeepEquals ("current-dateTime()", "not(true())");
            
        // operators
        assertDeepEquals ("a=b", "a=b");
        assertNotDeepEquals ("a=b", "c=d");
        assertNotDeepEquals ("a=b", "a!=b");

        // functions
        assertDeepEquals ("concat(a,b)", "concat(a,b)");
        assertNotDeepEquals ("concat(a,b)", "concat(b,c)");
        assertNotDeepEquals ("concat(a,b)", "substring-after(a,b)");
        assertNotDeepEquals ("string(a)", "a/string()");
            
        // predicates
        assertDeepEquals ("a[b]", "a[b]");
        assertNotDeepEquals ("a[b]", "b[a]");
            
        // sequence, subsequence
        assertNotDeepEquals ("(0,1,2)", "('x')");
        assertDeepEquals ("(0,1,2)[1]", "subsequence((0,1,2),1,1)");
        assertDeepEquals ("subsequence((0,1,2),1,3)", "subsequence((0,1,2),1,3)");
        assertNotDeepEquals ("subsequence((0,1,2),1,2)", "subsequence((0,1,2),1,1)");
    }
    
    private void assertDeepEquals (String xp1, String xp2) throws Exception {
        AbstractExpression cx1 = compile(xp1);
        AbstractExpression cx2 = compile(xp2);
        assertTrue (xp1 + " != " + xp2, cx1.deepEquals(cx2));
        assertTrue (xp2 + " != " + xp1, cx2.deepEquals(cx1));
    }

    private void assertNotDeepEquals (String xp1, String xp2) throws Exception {
        AbstractExpression cx1 = compile(xp1);
        AbstractExpression cx2 = compile(xp2);
        assertTrue (xp1 + " = " + xp2, ! cx1.deepEquals(cx2));
        assertTrue (xp2 + " = " + xp1, ! cx2.deepEquals(cx1));
    }

    private AbstractExpression compile (String xpath) throws SaxonApiException {
        return translator.exprFor (compiler.getXPathCompiler().compile(xpath).getUnderlyingExpression().getInternalExpression());
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

