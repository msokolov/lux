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
        compiler = new Compiler (IndexConfiguration.DEFAULT);
        translator = new SaxonTranslator (compiler.getProcessor().getUnderlyingConfiguration());
    }

    @Test
    public void testEquals () throws Exception {
        assertEquals ("a", "a");
        assertNotEquals ("c", "a");
        assertEquals ("/a/b/c", "/a/b/c");
        assertEquals ("xs:double(1)", "xs:double(1.0)");
        assertEquals ("1+2", "3");
        assertEquals ("concat('a','b')", "'ab'");
        // operators
        assertEquals ("a=b", "a=b");
        assertNotEquals ("a=b", "a!=b");
        // functions
        assertEquals ("concat(a,b)", "concat(a,b)");
        assertNotEquals ("concat(a,b)", "substring-after(a,b)");
        assertNotEquals ("string(a)", "a/string()");
        // predicates
        assertEquals ("a[b]", "a[b]");
        assertNotEquals ("a[b]", "b[a]");
        // sequence, subsequence
        assertEquals ("(0,1,2)", "(0,1,2)");
        assertEquals ("(0,1,2)[1]", "subsequence((0,1,2),1,1)");
        assertEquals ("subsequence((0,1,2),1,3)", "subsequence((0,1,2),1,3)");
        assertNotEquals ("subsequence((0,1,2),1,2)", "subsequence((0,1,2),1,1)");
    }
    
    private void assertEquals (String xp1, String xp2) throws Exception {
        AbstractExpression cx1 = compile(xp1);
        AbstractExpression cx2 = compile(xp2);
        assertTrue (xp1 + " != " + xp2, cx1.equals(cx2));
    }

    private void assertNotEquals (String xp1, String xp2) throws Exception {
        AbstractExpression cx1 = compile(xp1);
        AbstractExpression cx2 = compile(xp2);
        assertFalse (xp1 + " = " + xp2, cx1.equals(cx2));
    }

    private AbstractExpression compile (String xpath) throws SaxonApiException {
        return translator.exprFor (compiler.getXPathCompiler().compile(xpath).getUnderlyingExpression().getInternalExpression());
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

