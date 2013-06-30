package lux.junit;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.stream.StreamSource;

import lux.Compiler;
import lux.Evaluator;
import lux.QNameQueryTest;
import lux.QueryContext;
import lux.XdmResultSet;
import lux.index.XmlIndexer;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmValue;

import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

/**
 * Loads a test-suite file from the classpath whose name is the same as
 * this class, with an ".xml" extension.  Runs one test for each test-case 
 * in the file.
 */
public class QueryTestRunner extends ParentRunner<QueryTestCase> {

	// private String description;
    private List<QueryTestCase> cases;
    private HashMap<String,XdmNode> queryMap = new HashMap<String, XdmNode>();
    protected Evaluator eval;
    protected DocumentBuilder builder; 
    
    public QueryTestRunner(Class<? extends QNameQueryTest> klass) throws InitializationError {
        super (klass);
        eval = new Evaluator(new Compiler(getIndexer().getConfiguration()), null, null);
        builder = eval.getCompiler().getProcessor().newDocumentBuilder();
        queryMap = new HashMap<String, XdmNode>();
        cases = new ArrayList<QueryTestCase>(100);
        try {
            eval.getCompiler().setSearchStrategy (Compiler.SearchStrategy.NONE);
			loadTests ();
	        eval.getCompiler().setSearchStrategy (Compiler.SearchStrategy.LUX_SEARCH);
		} catch (IOException e) {
			throw new InitializationError(e);
		} catch (SaxonApiException e) {
			throw new InitializationError(e);
		}
    }

    /** @return an indexer whose optimizations are to be tested.
     * @throws InitializationError 
     */
    private XmlIndexer getIndexer() throws InitializationError {
		QNameQueryTest test;
		try {
			test = (QNameQueryTest) getTestClass().getOnlyConstructor().newInstance();
		} catch (Exception e) {
			throw new InitializationError (e);
		} 
		return test.getIndexer();
    }

    /**
     * @return a list of QueryTestCases that define the children of this Runner.
     */
    protected List<QueryTestCase> getChildren() {
        return cases;
    }

    /**
     * Returns a {@link Description} for the {@code child} test case, an
     *  element of the list returned by {@link ParentRunner#getChildren()}
     */
    protected Description describeChild(QueryTestCase child) {
        return Description.createTestDescription (getTestClass().getJavaClass(), child.getName());
    }

    /**
     * Runs the test corresponding to {@code child}, which can be assumed to be
     * an element of the list returned by {@link ParentRunner#getChildren()}.
     * Subclasses are responsible for making sure that relevant test events are
     * reported through {@code notifier}
     */
    protected void runChild(final QueryTestCase child, RunNotifier notifier) {
        runLeaf (new Statement() { @Override public void evaluate () { child.evaluate(eval); } }, 
                 describeChild(child), 
                 notifier);
    }
    
    /**
     * Reads a file from the classpath whose name is the name of the test
     * class with an .xml extension, from the same package as the test class,
     * parses the document and creates the QueryTestCases from it.
     * @throws FileNotFoundException 
     * @throws SaxonApiException 
     */
    protected void loadTests () throws IOException, SaxonApiException {
        String suiteFileName = getTestClass().getJavaClass().getSimpleName() + ".xml";
        XdmNode suite = readFile (suiteFileName);
        for (XdmItem queryItem : eval ("/test-suite/queries", suite)) {
        	loadQueries (queryItem);
        }
        loadTestCases (suite);
        // description = evalStr ("/test-suite/meta/title", suite);
        // /test-suite/meta/setup/keys
    }
    
    private void loadTestCases(XdmNode top) throws IOException, SaxonApiException {
        XdmValue kids = eval ("test-suite/test-cases/*", top);
        for (XdmItem item: kids) {
        	XdmNode kid = (XdmNode) item;
        	if (kid.getNodeName().getClarkName().equals("include")) {
                XdmNode tests = readFile (kid.getAttributeValue(new QName("file")));
                loadTestCases (tests);
        	}
        	else if (kid.getNodeName().getClarkName().equals("test-case")) {
        		addTestCase (kid);
        	}
        }
    }
    
    private void loadQueries(XdmItem top) throws IOException, SaxonApiException {
        for (XdmItem queryItem : eval ("*", top)) {
        	XdmNode node = (XdmNode) queryItem;
        	if (node.getNodeName().getLocalName().equals("query")) {
                String queryID = evalStr ("@id", queryItem);
                XdmValue queries = eval ("*", queryItem);
                if (queries.size() > 0) {
                	XdmNode firstQuery = (XdmNode) queries.iterator().next();
                	queryMap.put (queryID, firstQuery);
                }
        	} 
        	else if (node.getNodeName().getLocalName().equals("include")) {
        		String fileName = evalStr ("@file", node);
        		XdmNode include = readFile (fileName);
        		XdmValue queries = eval("queries", include);
        		for (XdmItem q : queries) {
        			loadQueries (q);
        		}
        	}
         }
	}

	XdmNode readFile (String fileName) throws IOException, SaxonApiException {
        InputStream in = getTestClass().getJavaClass().getResourceAsStream (fileName);
        if (in == null) {
            throw new FileNotFoundException (fileName);
        }
        XdmNode node = builder.build(new StreamSource (in));
        in.close();
    	return node;
    }

	private void addTestCase(XdmItem testItem) {
		String name = evalStr ("@name", testItem);
		String queryText = evalStr ("query", testItem);
		boolean expectError = evalStr ("exists(expect/error)", testItem).equals("true");
		String expectedError  = evalStr ("expect/error", testItem);
		List<XdmNode> expectedQueries = getExpectedQueries (queryMap, testItem);
		String expectedResultType = evalStr ("expect/query[1]/@type", testItem);
		String expectedOrderBy= evalStr ("expect/query[1]/@order-by", testItem);
		QueryTestResult expectedResult = new QueryTestResult 
		    (expectError, expectedError, getExpectedQueryText(testItem), expectedQueries,
		     expectedResultType, expectedOrderBy);
		QueryTestCase testCase = newTestCase (name, queryText, expectedResult);
		cases.add (testCase);
	}
	
	protected QueryTestCase newTestCase (String name, String queryText, QueryTestResult expectedResult) {
		return new QueryTestCase (name, queryText, expectedResult);
	}
    
    private String getExpectedQueryText (XdmItem testItem) {
        String expectedQueryText  = evalStr ("expect/query", testItem);
        if (expectedQueryText != null) {
        	// replace query id tokens like #QUERY#
        	Pattern pat= Pattern.compile("#(\\w+)#");
        	Matcher matcher = pat.matcher(expectedQueryText);
        	while (matcher.find()) {
        		XdmNode query = queryMap.get(matcher.group(1));
        		if (query == null) {
        			throw new RuntimeException ("Test case references undefined query id=" + matcher.group(1));
        		}
        		expectedQueryText = matcher.replaceFirst(query.toString().replaceAll("\r?\n\\s*", ""));
        		matcher = pat.matcher(expectedQueryText);
        	}
        }
        return expectedQueryText;
    }

    private List<XdmNode> getExpectedQueries (HashMap<String,XdmNode> queryMap, XdmItem testItem) {
        List<XdmNode> expectedQueries = new ArrayList<XdmNode>();
        for (XdmItem queryID : eval ("expect/query/@id", testItem)) {
        	String expectedQueryID  = queryID.getStringValue();
        	if (expectedQueryID != null) {
        		XdmNode query = queryMap.get(expectedQueryID);
        		if (query == null) {
        			throw new RuntimeException ("Test case references undefined query id=" + expectedQueryID);
        		}
        		expectedQueries.add(query);
        	}
        }
        return expectedQueries;
    }
    
    protected XdmValue eval (String xpath, XdmItem contextItem) {
        QueryContext context = new QueryContext();
        context.setContextItem (contextItem);
        XdmResultSet result = eval.evaluate (xpath, context);
        return result.getXdmValue();
    }

    protected String evalStr (String xpath, XdmItem contextItem) {
    	// get the string value of the first item returned by evaluating the path, or empty string if none. 
        XdmValue value = eval (xpath, contextItem);
        if (value.size() == 0) {
        	return null;
        }
		XdmItem item = value.itemAt(0);
        return item.getStringValue();
    }
    
}
