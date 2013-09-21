package lux.xqts;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import lux.XdmResultSet;
import lux.xqts.TestCase.VariableBinding.Type;
import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.functions.DeepEqual;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;

import org.apache.commons.io.IOUtils;

/**
 * represents an XQTS test case
 *
 */
public class TestCase {

    private final Catalog catalog;
    private final String name;
    private final String path;
    private final String scenario;
    private final String queryName;
    private final boolean xpath2;
    private final XdmValue contextItem;
    private final ComparisonMode comparisonMode;
    
    private HashMap<String,VariableBinding> externalVariables;
    
    private final String queryText;
    //private final String inputFileText;
    private String[] outputFileText;
    
    private final boolean expectError;
    private String principalData;

    static final String XQTS_NS = "http://www.w3.org/2005/02/query-test-XQTSCatalog";
    static final QName COMPARE = new QName("compare");
    static final QName IS_X_PATH2 = new QName("is-XPath2");
    static final QName SCENARIO = new QName("scenario");
    static final QName FILE_PATH = new QName("FilePath");
    static final QName NAME = new QName("name");
    static final QName VARIABLE = new QName("variable");
    static final QName ROLE = new QName("role");
    static final QName QUERY = new QName(XQTS_NS, "query");
    static final QName INPUT_QUERY = new QName(XQTS_NS, "input-query");
    static final QName INPUT_FILE = new QName(XQTS_NS, "input-file");
    static final QName INPUT_URI = new QName(XQTS_NS, "input-URI");
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
        externalVariables = new HashMap<String, VariableBinding>();
        this.catalog = catalog;
        
        XdmNode query = (XdmNode) testCase.axisIterator(Axis.CHILD, QUERY).next();
        queryName = query.getAttributeValue(NAME);

        XdmSequenceIterator context = testCase.axisIterator(Axis.CHILD, CONTEXT_ITEM);
        if (context.hasNext()) {
            String contextItemFileName = context.next().getStringValue();
            contextItem = catalog.getBuilder().build(new File(catalog.getSourceFileByID(contextItemFileName)));
        } else {
            contextItem = null;
        }
        comparisonMode = readOutputText(testCase);
        
        File queryFile = new File (getQueryPath(queryName));
        String text = IOUtils.toString (new FileInputStream(queryFile));
        queryText = text;
        
        bindExternalVariables(testCase, INPUT_FILE, VariableBinding.Type.FILE);
        bindExternalVariables(testCase, INPUT_URI, VariableBinding.Type.URI);
        
        // Are there input queries? If so, record the bindings for later evaluation
        XdmSequenceIterator inputQuery = testCase.axisIterator(Axis.CHILD, INPUT_QUERY);
        while (inputQuery.hasNext()) {
            XdmNode q = (XdmNode) inputQuery.next();
            String filename = q.getAttributeValue(NAME);
            VariableBinding binding = new VariableBinding();
            binding.type = Type.FILE;
            binding.value = getQueryPath(filename);
            externalVariables.put(q.getAttributeValue(VARIABLE), binding);
        }
        
        XdmSequenceIterator errors = testCase.axisIterator(Axis.CHILD, EXPECTED_ERROR);
        expectError = errors.hasNext();
        
        catalog.putTestCase(name, this);
    }

    private void bindExternalVariables(XdmNode testCase, QName elementName, Type type) {
        XdmSequenceIterator input = testCase.axisIterator(Axis.CHILD, elementName);
        XdmNode inputFileNode = null;
        while (input.hasNext()) {
            inputFileNode = (XdmNode) input.next();
            String inputVariable = inputFileNode.getAttributeValue(VARIABLE);
            String role = inputFileNode.getAttributeValue(ROLE);
            String inputFileName = inputFileNode.axisIterator(Axis.CHILD).next().getStringValue();
            VariableBinding binding = new VariableBinding();
            binding.type = type;
            binding.role = role;
            binding.value = catalog.getSourceFileByID(inputFileName);
            externalVariables.put(inputVariable, binding);
            if ("principal-data".equals (role)) {
                principalData = inputFileName;
            }
        }
    }

    private String getQueryPath(String filename) {
        return catalog.getDirectory() + "/Queries/XQuery/" + path + '/' + filename + ".xq";
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

    public String getBenchmarkQueryText() {
        String benchQueryText = queryText;
        for (Map.Entry<String,VariableBinding> entry : externalVariables.entrySet()) {
            VariableBinding binding = entry.getValue();
            String varName = entry.getKey();
            if ("principal-data".equals (binding.role)) {
                benchQueryText = benchQueryText.replace
                    ("declare variable $" + varName + " external;",
                     "declare variable $" + varName + " := collection();");
            }
        }
        return benchQueryText;
    }

    public boolean isXpath2() {
        return xpath2;
    }

    public ComparisonMode getComparisonMode() {
        return comparisonMode;
    }


    public String[] getOutputText() {
        return outputFileText;
    }

    public Boolean compareResult(Iterable<?> results) throws SaxonApiException, XPathException {
        switch (getComparisonMode()) {
        case Fragment:
        case Text:
            String result = results == null ? "" : resultToString(results);
            if ("-0".equals(result)) {
                result = "0";
            }
            boolean isNode = (!result.isEmpty() && results.iterator().next() instanceof XdmNode);
            for (String output : getOutputText()) {
                if ("-0".equals(output)) {
                    output = "0";
                }
                XdmNode docWrapped = createWrappedNode(output);
                if (isNode) {
                    XdmNode resultDoc = createWrappedNode(result);
                    if (areNodesEqual (docWrapped, resultDoc)) {
                        return true;
                    }
                } else {
                    if (normalizeWhitespace(result).equals(normalizeWhitespace(unescape(output))))
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

    private Object normalizeWhitespace(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    private XdmNode createWrappedNode(Object node) throws SaxonApiException {
        String s = node.toString();
        // remove any xml declaration; a hack sure
        s = s.replaceFirst("<\\?xml[^>]+>", "");
        return catalog.getBuilder().build(new StreamSource(new ByteArrayInputStream(("<a>"+s+"</a>").getBytes())));
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
            // compare root elements
            if (node1.getNodeKind() == XdmNodeKind.DOCUMENT) {
                node1 = (XdmNode) node1.axisIterator(Axis.CHILD).next();
            }
        }
        Configuration config = catalog.getProcessor().getUnderlyingConfiguration();
        XPathContext conversionContext = config.getConversionContext();
        return DeepEqual.deepEquals(
                node1.getUnderlyingNode().iterate(),
                node2.getUnderlyingNode().iterate(),
                new GenericAtomicComparer(CodepointCollator.getInstance(),
                        conversionContext),
                conversionContext,
                DeepEqual.INCLUDE_PREFIXES |
                DeepEqual.EXCLUDE_WHITESPACE_TEXT_NODES |
                    DeepEqual.INCLUDE_COMMENTS |
                    DeepEqual.COMPARE_STRING_VALUES |
                    DeepEqual.INCLUDE_PROCESSING_INSTRUCTIONS);
    }
    
    private String unescape (String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;",">").replace("\r\n", "\n");
    }
    
    public String getPrincipalData () {
        return principalData;
    }
    
    public XdmValue getContextItem () {
        return contextItem;
    }
    
    @Override
    public String toString () {
        return "XQueryTestCase{" + name + "}";
    }

    public boolean isExpectError() {
        return expectError;
    }
    
    public HashMap<String,VariableBinding> getExternalVariables () {
        return externalVariables;
    }

    static class VariableBinding {
        enum Type { URI, FILE };
        String value;
        String role;
        Type type;
    }

    public Boolean compareResult(XdmResultSet results, XdmValue value) throws XPathException, SaxonApiException {
        XdmSequenceIterator iter = value.iterator();
        for (Object o: results) {
            XdmItem result = (XdmItem) o;
            XdmItem item = iter.next();
            if (item.isAtomicValue()) {
                if (! (item.getStringValue().equals(result.getStringValue()))) {
                    System.err.println (item.getStringValue() + " is not " + result.getStringValue());
                    return false;
                }
            }
            else {
                XdmNode expected = nodeFor(item);
                XdmNode node = nodeFor((XdmItem) result);
                if (! areNodesEqual (expected, node)) {
                    //System.err.println (node.toString() + " is not " + expected.toString());
                    return false;
                }
            }
        }
        return true;
    }

    private XdmNode nodeFor(XdmItem item) throws SaxonApiException {
        XdmNode itemNode;
        if (item.isAtomicValue()) {
            itemNode = createWrappedNode(item);
        } else {
            itemNode = (XdmNode) item;
        }
        return itemNode;
    } 
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
