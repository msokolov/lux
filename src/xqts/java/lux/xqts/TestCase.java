package lux.xqts;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.functions.DeepEqual;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;

import org.apache.commons.io.IOUtils;

/**
 * represents an XQTS test case
 *
 */
public class TestCase {

    private static final Pattern INPUT_VARIABLE_PATTERN = Pattern.compile("\\(: insert-start :\\).*\\(: insert-end :\\)", Pattern.DOTALL);
    private final Catalog catalog;
    private final String name;
    private final String path;
    private final String scenario;
    private final String queryName;
    private final boolean xpath2;
    //private final String inputFileName;
    private final XdmValue contextItem;
    private final ComparisonMode comparisonMode;
    
    private final String queryText;
    //private final String inputFileText;
    private String[] outputFileText;
    private final boolean expectError;

    static final String XQTS_NS = "http://www.w3.org/2005/02/query-test-XQTSCatalog";
    static final QName COMPARE = new QName("compare");
    static final QName IS_X_PATH2 = new QName("is-XPath2");
    static final QName SCENARIO = new QName("scenario");
    static final QName FILE_PATH = new QName("FilePath");
    static final QName NAME = new QName("name");
    static final QName VARIABLE = new QName("variable");
    static final QName QUERY = new QName(XQTS_NS, "query");
    static final QName INPUT_FILE = new QName(XQTS_NS, "input-file");
    static final QName OUTPUT_FILE = new QName(XQTS_NS, "output-file");
    static final QName CONTEXT_ITEM = new QName(XQTS_NS, "contextItem");
    private static final QName EXPECTED_ERROR = new QName(XQTS_NS, "expected-error");   
    
    public enum ComparisonMode {
        XML, Text, Fragment, Ignore, Inspect;
    }
    
    public TestCase (XdmNode testCase, Catalog catalog) throws FileNotFoundException, IOException, SaxonApiException {
        name = testCase.getAttributeValue(NAME);
        path = testCase.getAttributeValue(FILE_PATH);
        scenario = testCase.getAttributeValue(SCENARIO);
        xpath2 = Boolean.valueOf(testCase.getAttributeValue(IS_X_PATH2));
        this.catalog = catalog;
        
        XdmNode query = (XdmNode) testCase.axisIterator(Axis.CHILD, QUERY).next();
        queryName = query.getAttributeValue(NAME);

        XdmSequenceIterator context = testCase.axisIterator(Axis.CHILD, CONTEXT_ITEM);
        if (context.hasNext()) {
            String contextItemFileName = context.next().getStringValue();
            contextItem = catalog.getBuilder().build(new File(catalog.getDirectory() + "/TestSources/" + contextItemFileName + ".xml"));
        } else {
            contextItem = null;
        }
        comparisonMode = readOutputText(testCase);
        
        File queryFile = new File (catalog.getDirectory() + "/Queries/XQuery/" + path + '/' + queryName + ".xq");
        String text = IOUtils.toString (new FileInputStream(queryFile));
        
        // remove the declaration of the input variable in the query text and replace its uses with doc().
        XdmSequenceIterator input = testCase.axisIterator(Axis.CHILD, INPUT_FILE);
        XdmNode inputFileNode = null;
        while (input.hasNext()) {
            inputFileNode = (XdmNode) input.next();
            String inputFileName = inputFileNode.axisIterator(Axis.CHILD).next().getStringValue();
            String inputVariable = inputFileNode.getAttributeValue(VARIABLE);
            if (!inputVariable.isEmpty()) {
                text = INPUT_VARIABLE_PATTERN.matcher(text).replaceFirst("");
                text = text.replace('$' + inputVariable, "fn:doc('" + catalog.getDirectory() + 
                        "/TestSources/" + inputFileName + ".xml')");
            }
        }
        queryText = text;
        
        XdmSequenceIterator errors = testCase.axisIterator(Axis.CHILD, EXPECTED_ERROR);
        expectError = errors.hasNext();
        
        catalog.putTestCase(name, this);
    }

    private ComparisonMode readOutputText(XdmNode testCase) throws IOException, FileNotFoundException {
        XdmSequenceIterator output = testCase.axisIterator(Axis.CHILD, OUTPUT_FILE);
        if (!output.hasNext()) {
            return ComparisonMode.Ignore;
        }
        // first count the output files (and get the comparison mode from the first one)
        XdmNode outputFileNode = (XdmNode) output.next();
        int outputFileCount = 1;
        ComparisonMode mode = ComparisonMode.valueOf(outputFileNode.getAttributeValue(COMPARE)); 
        while (output.hasNext()) {
            ++outputFileCount;
            output.next();
        }
        outputFileText = new String[outputFileCount];
        // now get the text from each node
        outputFileCount = 0;
        output = testCase.axisIterator(Axis.CHILD, OUTPUT_FILE);
        while (output.hasNext()) { 
            outputFileNode = (XdmNode) output.next();
            XdmSequenceIterator outputFileSeq = outputFileNode.axisIterator(Axis.CHILD);
            if (outputFileSeq.hasNext()) {
                String outputFileName = outputFileSeq.next().getStringValue();
                File outputFile = new File (catalog.getDirectory() + "/ExpectedTestResults/" + path + '/' + outputFileName);            
                String text = IOUtils.toString (new FileInputStream(outputFile));
                outputFileText[outputFileCount++] = text;
            } else {
                outputFileText = null;
            }
        }
        return mode;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getScenario() {
        return scenario;
    }

    public String getQueryName() {
        return queryName;
    }

    public String getQueryText() {
        return queryText;
    }

    public boolean isXpath2() {
        return xpath2;
    }

    public ComparisonMode getComparisonMode() {
        return comparisonMode;
    }

    public String getInputText() {
        // String text = IOUtils.toString (new FileInputStream(directory + "/TestSources/" + inputFileName));
        return null;
    }

    public String[] getOutputText() {
        return outputFileText;
    }

    public Boolean compareResult(Iterable<?> results) throws SaxonApiException, XPathException {
        switch (getComparisonMode()) {
        case Fragment:
        case Text:
            String result = results == null ? "" : resultToString(results);
            boolean isNode = (!result.isEmpty() && results.iterator().next() instanceof XdmNode);
            for (String output : getOutputText()) {
                XdmNode docWrapped = createWrappedNode(output);
                if (isNode) {
                    XdmNode resultDoc = createWrappedNode(result);
                    if (areNodesEqual (docWrapped, resultDoc)) {
                        return true;
                    }
                } else {
                    if (result.equals(unescape(output)))
                        return true;
                }
            }
            return false;
        case XML:
            for (String output : getOutputText()) {
                //output = output.replace("\r\n", "\n");
                XdmNode doc = catalog.getBuilder().build(new StreamSource(new ByteArrayInputStream(output.getBytes())));
                // true if any of the docs is equal??
                for (Object node : results) {
                    if (areNodesEqual (doc, (XdmNode) node)) {
                        return true;
                    }
                }
            }
            return false;
        case Ignore:
        case Inspect:
        default:
            return null;
        }
    }

    private XdmNode createWrappedNode(Object node) throws SaxonApiException {
        return catalog.getBuilder().build(new StreamSource(new ByteArrayInputStream(("<a>"+node+"</a>").getBytes())));
    }

    public static String resultToString(Iterable<?> results) throws XPathException {
        Iterator<?> iterator = results.iterator();
        if (!iterator.hasNext()) {
            return "";
        }
        Object result = iterator.next();
        StringBuilder buf = new StringBuilder (resultToString (result));
        boolean lastNode = result instanceof XdmNode;
        while (iterator.hasNext()) {
            result = iterator.next();
            if (! (result instanceof XdmNode) && !lastNode) {
                buf.append (' ');
            }
            buf.append (resultToString (result));
            lastNode = result instanceof XdmNode;
        }
        return buf.toString();
    }
    
    public static String resultToString (Object o) throws XPathException {
        if (o instanceof XdmNode) {
            return resultToString((XdmNode) o);
        }
        return o.toString();        
    }

    public static String resultToString (XdmNode node) throws XPathException {
        StringWriter sw = new StringWriter();
        Properties props = new Properties();
        props.setProperty("method", "xml");
        props.setProperty("indent", "no");
        props.setProperty("omit-xml-declaration", "yes");
        QueryResult.serialize(node.getUnderlyingNode(), new StreamResult(sw), props);
        return sw.toString();
    }
    
    private boolean areNodesEqual (XdmNode node1, XdmNode node2) throws XPathException {
        if (node2.getNodeKind() != XdmNodeKind.DOCUMENT) {
            // node1 will always be a document - compare against its root element
            node1 = (XdmNode) node1.axisIterator(Axis.CHILD).next();
        }
        return DeepEqual.deepEquals(
                SingletonIterator.makeIterator(node1.getUnderlyingNode()),
                SingletonIterator.makeIterator(node2.getUnderlyingNode()),
                new GenericAtomicComparer(CodepointCollator.getInstance(),
                        catalog.getProcessor().getUnderlyingConfiguration().getConversionContext()),
                catalog.getProcessor().getUnderlyingConfiguration(),
                DeepEqual.INCLUDE_PREFIXES |
                    DeepEqual.INCLUDE_COMMENTS |
                    DeepEqual.COMPARE_STRING_VALUES |
                    DeepEqual.INCLUDE_PROCESSING_INSTRUCTIONS);
    }
    
    private String unescape (String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("\r\n", "\n");
    }

    private String escape (String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }
    
    public XdmValue getContextItem () {
        return contextItem;
    }
    
    public String toString () {
        return "XQueryTestCase{" + name + "}";
    }

    public boolean isExpectError() {
        return expectError;
    }
 
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
