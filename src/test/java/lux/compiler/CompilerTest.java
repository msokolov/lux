package lux.compiler;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import lux.Compiler;
import lux.Evaluator;
import lux.exception.LuxException;
import lux.index.IndexConfiguration;
import lux.index.XmlIndexer;
import net.sf.saxon.s9api.XQueryExecutable;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;

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
    public void testModuleImport () throws Exception {
        assertQuery ("test", "import-module.xqy"); 
    }
    
    @Test 
    public void testTypedVariable() throws Exception {
        assertQuery ("5", "typed-variable.xqy"); 
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
    
    private void assertQuery (String result, String queryFileName) throws IOException, LuxException, URISyntaxException {
        ClassLoader classLoader = getClass().getClassLoader();
        URL url = classLoader.getResource ("lux/compiler/" + queryFileName);
        String query = IOUtils.toString(url.openStream(), "utf-8");
        URI uri = url.toURI();
        XQueryExecutable cq = compiler.compile(query, null, uri);
        System.err.println (compiler.getLastOptimized());
        assertEquals (result, eval.evaluate(cq).getXdmValue().toString());
    }
    
}
