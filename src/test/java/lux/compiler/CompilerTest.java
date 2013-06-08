package lux.compiler;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import javax.xml.transform.stream.StreamSource;

import lux.Compiler;
import lux.Compiler.SearchStrategy;
import lux.Evaluator;
import lux.QueryContext;
import lux.XdmResultSet;
import lux.exception.LuxException;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import lux.xml.QName;
import lux.xpath.AbstractExpression;
import net.sf.saxon.lib.CollectionURIResolver;
import net.sf.saxon.lib.StandardCollectionURIResolver;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.XdmValue;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests that focus on the compiler and optimizer, and do not rely on any stored
 * documents
 */
public class CompilerTest {

    protected Compiler compiler;
    protected Evaluator eval;
    protected SaxonTranslator translator;
    
    @Before public void setup () {
        XmlIndexer indexer = new XmlIndexer(IndexConfiguration.INDEX_QNAMES);
        compiler = new Compiler(indexer.getConfiguration());
        eval = new Evaluator(compiler, null, null);
        translator = compiler.makeTranslator();
    }
    
    @Test
    public void testSearchStrategy () throws Exception {
    	assertSame (SearchStrategy.LUX_SEARCH, compiler.getSearchStrategy());
    	compiler.setSearchStrategy(SearchStrategy.NONE);
    	assertSame (SearchStrategy.NONE, compiler.getSearchStrategy());
    }
    
    @Test
    public void testResolver () throws Exception {
    	CollectionURIResolver resolver = compiler.getDefaultCollectionURIResolver();
		assertNotNull (resolver);
		assertTrue (resolver.getClass().getName(), resolver instanceof StandardCollectionURIResolver);
    }
    
    @Test
    public void testInititalizeEXPath () throws Exception {
    	System.setProperty("org.expath.pkg.saxon.repo", "fail");
    	// ensure that Compiler can be created with invalid EXPath repo - just logs an error
    	new Compiler(IndexConfiguration.DEFAULT);
    	System.setProperty("org.expath.pkg.saxon.repo", "");
    }
    
    @Test public void testXPathCompiler() throws SaxonApiException {
    	XPathExecutable ex = compiler.getXPathCompiler().compile("/");
    	AbstractExpression expr = 
    		new SaxonTranslator(compiler.getProcessor().getUnderlyingConfiguration()).
    			exprFor(ex.getUnderlyingExpression().getInternalExpression());
    	assertEquals ("(/)", expr.toString());
    }
    
    @Test 
    public void testModuleImport () throws Exception {
        assertQuery ("test", "import-module.xqy"); 
    }
    
    @Test 
    public void testTypedVariable() throws Exception {
        assertQuery ("13", "typed-variable.xqy"); 
    }
    
    @Test 
    public void testTypedFunction() throws Exception {
        assertQueryError ("A sequence of more than one item is not allowed as the result of function local:int-sequence() (1, 2) ", "typed-function.xqy"); 
    }
    
    @Test
    public void testTypedNodes() throws Exception {
        assertQuery ("2", "typed-nodes.xqy");
    }

    @Test
    public void testTypedNodeFail() throws Exception {
        assertQueryError ("Required item type of value in 'treat as' expression is element('':foo); supplied value has item type element('':bar)", "typed-node-fail.xqy");
    }
    
    @Test 
    public void testExtVarType() throws Exception {
        XQueryExecutable query = compileQuery ("ext-var-type.xqy");
        QueryContext context = new QueryContext ();
        context.bindVariable(new QName("http://localhost/", "integer"), new XdmAtomicValue("one"));
        // query expects an integer, not a string
        XdmResultSet result = eval.evaluate(query, context);
        assertEquals ("expected one error", 1, result.getErrors().size());
        assertEquals ("Required item type of value of variable $local:integer is xs:integer; supplied value has item type xs:string", result.getErrors().get(0).getMessage());
    }
    
    @Test
    public void testExtVar () throws Exception {
        XQueryExecutable query = compileQuery ("ext-var.xqy");
        QueryContext context = new QueryContext ();
        context.bindVariable(new QName("http://localhost/", "integer"), new XdmAtomicValue("1"));
        XdmResultSet result = eval.evaluate(query, context);
        assertEquals ("expected no results", false, result.getXdmValue().iterator().hasNext());
    }
    
    @Test 
    public void testCommentConstructor() throws Exception {
        assertQuery ("<!--test-->", "comment-constructor.xqy"); 
    }
    
    @Test 
    public void testPIConstructor() throws Exception {
        assertQuery ("<?php test?>", "processing-instruction-constructor.xqy"); 
    }
    
    @Test 
    public void testElementConstructor() throws Exception {
        assertQuery ("<xml:Father xml:id=\"Bosak\">John Bosak</xml:Father>", "element-constructor.xqy"); 
    }
    
    @Test
    public void testInstanceOf() throws Exception {
        assertQuery ("test,testtest1,test", "instance-of.xqy"); 
    }
    
    @Test
    public void testSatisfies() throws Exception {
        assertQuery ("yesno", "satisfies.xqy"); 
    }
    
    @Test
    public void testIntersect() throws Exception {
        assertQuery ("test", "intersect.xqy"); 
    }
    
    @Test
    public void testMinus() throws Exception {
        assertQuery ("1", "minus.xqy");
        assertQuery ("1", "minus-1.xqy"); 
    }
    
    @Test
    public void testVariableShadowing () throws Exception {
        assertQuery ("1", "variable-shadowing.xqy");
    }
    
    @Test
    public void testWhereClause () throws Exception {
    	// this is supposed to test the translation and optimization of where clauses (a bit)
    	// but Saxon converts where clauses to predicates pretty much universally, so
    	// this uses an "at" expression to force the where clause to be retained
    	assertQuery ("4", "count-primes-less-than-10.xqy");
    }
    
    @Test
    public void testParentExpression () throws Exception {
        assertQuery ("2", "count-parents.xqy");
    }
    
    @Test
    public void testComputedAttributeName () throws Exception {
        assertQueryError (null, "computed-attribute-name.xqy");
    }
    
    @Test
    public void testK2ForExprWithout10 () throws Exception {
        // exercise some obscure Saxon code rewriting path involving LetClause
        assertQuery ("111222333", "K2-ForExprWithout-10.xqy");
    }
    
    @Test
    public void testFollowing () throws Exception {
        assertQuery ("true", "following.xqy");
    }
    
    @Test
    public void testEmptyVariable() throws Exception {
        assertQuery ("", "empty-variable.xqy");
    }
    
    @Test
    public void testOptionalVariable() throws Exception {
        assertQuery ("b", "optional-variable.xqy");
    }
    
    @Test
    public void testNodeNameWildcards () throws Exception {
        assertQuery ("a xs:bxs:ba", "node-wildcard.xqy");
    }
    
    @Test
    public void testStrayOperators () throws Exception {
    	assertQuery ("-2", "stray-operators.xqy");
    }

    @Test
    public void testForAt() throws Exception {
    	assertQuery ("1148", "for-at.xqy");
    }

    @Test
    public void testReverse() throws Exception {
    	assertQuery ("10", "reverse.xqy");
    }
    
    @Test
    public void testIntegerRange() throws Exception {
    	assertQuery ("true", "integer-range.xqy");
    }
    
    @Test
    public void testGeneralizeOperator() throws Exception {
        QueryContext context = new QueryContext();
        String input = "<works><employee name='Jane Doe 1' gender='female'><empnum>E1</empnum><pnum>P1</pnum><hours>40</hours></employee></works>";
        XdmNode inputDoc = compiler.getProcessor().newDocumentBuilder().build(new StreamSource (new StringReader (input)));
        context.bindVariable(new QName("input-context1"), inputDoc);
        assertQuery ("true", "generalize-operator.xqy", context);
    }
    
    @Test
    public void testReturnExpr018() throws Exception {
        QueryContext context = new QueryContext();
        String input = "<Drive id='0'><Folder><File><Stream><StreamSize>10</StreamSize></Stream></File></Folder></Drive>";
        XdmNode inputDoc = compiler.getProcessor().newDocumentBuilder().build(new StreamSource (new StringReader (input)));
        context.bindVariable(new QName("input-context"), inputDoc);
        assertQuery ("falsetruefalsetruefalsetrue", "ReturnExpr018.xqy", context);
    }
    
    @Test
    public void testDateSubtraction() throws Exception {
        //assertQuery ("xs:dayTimeDuration(\"P26997D\")", "day-time.xqy");
        assertQuery ("P26997D", "day-time.xqy");
    }
    
    private void assertQuery (String result, String queryFileName) throws IOException, LuxException, URISyntaxException {
        assertQuery (result, queryFileName, null);
    }
    
    private void assertQuery (String result, String queryFileName, QueryContext context) throws IOException, LuxException, URISyntaxException {
        XdmResultSet resultSet = evalQuery(queryFileName, context);
        if (resultSet.getErrors().size() > 0) {
            fail ("Got unexpected error: " + resultSet.getErrors().get(0).getMessageAndLocation());
        }
        XdmValue value = resultSet.getXdmValue();
        StringBuilder buf = new StringBuilder();
        XdmSequenceIterator iter = value.iterator();
        while (iter.hasNext()) {
            buf.append(iter.next());
        }
        assertEquals (result, buf.toString());
    }
    
    private XdmResultSet evalQuery(String queryFileName, QueryContext context) throws IOException, URISyntaxException {
        XQueryExecutable cq = compileQuery(queryFileName);
        return eval.evaluate(cq, context);
    }

    private XQueryExecutable compileQuery(String queryFileName) throws IOException, URISyntaxException {
        ClassLoader classLoader = getClass().getClassLoader();
        URL url = classLoader.getResource ("lux/compiler/" + queryFileName);
        String query = IOUtils.toString(url.openStream(), "utf-8");
        URI uri = url.toURI();
        XQueryExecutable cq = compiler.compile(query, null, uri);
        // System.err.println (compiler.getLastOptimized());
        return cq;
    }
    
    private void assertQueryError (String error, String queryFileName) throws IOException, URISyntaxException {
        assertQueryError (error, queryFileName, null);
    }
    
    private void assertQueryError (String error, String queryFileName, QueryContext context) throws IOException, URISyntaxException {
        XdmResultSet result = evalQuery (queryFileName, context);
        assertFalse ("expected exception '" + error + "' not thrown; got results=" + result.getXdmValue(), 
                    result.getErrors().isEmpty());
        if (error != null) {
            assertEquals (error, result.getErrors().get(0).getMessage());
        }
    }
    
}
