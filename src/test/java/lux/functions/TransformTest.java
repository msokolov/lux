package lux.functions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import lux.Evaluator;
import lux.QueryContext;
import lux.XdmResultSet;
import lux.xml.QName;
import net.sf.saxon.s9api.XdmAtomicValue;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Test;

public class TransformTest extends XQueryTest {
    
    @Test
    public void testBasicQuery () {
        // just make sure everything is wired up properly to run XQuery
        assertXQuery ("2", "1 + 1");
        assertXQuery ("ABC", "concat('A','B','C')");
    }
    
    @Test
    public void testTransform1 () throws Exception {
        // test a query that calls lux:transform
        assertXQueryFile ("2", "transform-1.xqy");
    }
    
    @Test
    public void testOutputURIResolution() throws Exception {
        // test a transform that writes a document using result-document
    	assertXQueryFile (null, "transform-result-document.xqy", 
    			"Attempted to write document /doc/1 to a read-only Evaluator");
    	RAMDirectory dir = new RAMDirectory();
		Evaluator writable = Evaluator.createEvaluator (dir);
        String query = IOUtils.toString(TransformTest.class.getResourceAsStream("transform-result-document.xqy"));
		writable.evaluate(query);
		// TODO: make this more convenient.  Have an auto-commit at the end of each evaluation
		// (only when there have been any writes?) and re-open the reader so we can see the results
		// in subsequent queries
		writable.getDocWriter().commit(writable);
		// re-open so that we see the results here
		writable.reopenSearcher();
        XdmResultSet result = writable.evaluate("collection()[1]");
        assertEquals ("1", result.getXdmValue().itemAt(0).getStringValue());
        result = writable.evaluate("doc('/doc/1')");
        assertEquals ("1", result.getXdmValue().itemAt(0).getStringValue());
    }
    
    @Test
    public void testFileURIResolution() throws Exception {
    	XdmResultSet result = evaluator.evaluate("doc('file:src/test/resources/lux/reader-test.xml')");
    	assertNotNull (result.getXdmValue().itemAt(0));
    	// TODO: enable setting the base-uri in the context and test
    	// resolution of relative uris against that
    	// result = evaluator.evaluate("doc('src/test/resources/lux/reader-test.xml')");
    	// assertNotNull (result.getXdmValue().itemAt(0));
    }
    
    @Test
    public void testTransformError () throws Exception {
        // test a query that doesn't compile
        assertXQueryFile (null, "transform-error.xqy", "The supplied file does not appear to be a stylesheet");
    }
    
    @Test
    public void testTransformContext () throws Exception {
        // test a stylesheet that expects an external variable - attempt
        // to bind one, but it is not seen by the stylesheet
        String query = IOUtils.toString(TransformTest.class.getResourceAsStream("transform-context.xqy"));
        QueryContext context = new QueryContext();
        context.bindVariable(new QName("external-var"), new XdmAtomicValue(10));
        XdmResultSet results = evaluator.evaluate(query, context);
        assertEquals ("got an unexpected error", 0, results.getErrors().size());
        assertEquals ("undefined", results.iterator().next().toString());
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
