package lux.xqts;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.commons.io.IOUtils;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;

/**
 * represents an XQTS test case
 *
 */
public class TestCase {

    private final String name;
    private final String path;
    private final String scenario;
    private final String queryName;
    private final boolean xpath2;
    private final String inputFileName;
    private final String outputFileName;
    private final ComparisonMode comparisonMode;
    
    private final String queryText;
    private final String inputFileText;
    private final String outputFileText;

    public static final String XQTS_NS = "http://www.w3.org/2005/02/query-test-XQTSCatalog";
    private static final QName COMPARE = new QName("compare");
    private static final QName IS_X_PATH2 = new QName("is-XPath2");
    private static final QName SCENARIO = new QName("scenario");
    private static final QName FILE_PATH = new QName("FilePath");
    private static final QName NAME = new QName("name");    
    private static final QName QUERY = new QName(XQTS_NS, "query");
    private static final QName INPUT_FILE = new QName(XQTS_NS, "input-file");
    private static final QName OUTPUT_FILE = new QName(XQTS_NS, "output-file");   
    
    public enum ComparisonMode {
        Text;
    }
    
    public TestCase (XdmNode testCase, String directory) throws FileNotFoundException, IOException {
        name = testCase.getAttributeValue(NAME);
        path = testCase.getAttributeValue(FILE_PATH);
        scenario = testCase.getAttributeValue(SCENARIO);
        xpath2 = Boolean.valueOf(testCase.getAttributeValue(IS_X_PATH2));
        
        XdmNode query = (XdmNode) testCase.axisIterator(Axis.CHILD, QUERY).next();
        queryName = query.getAttributeValue(NAME);

        XdmNode inputFileNode = (XdmNode) testCase.axisIterator(Axis.CHILD, INPUT_FILE).next();
        inputFileName = inputFileNode.axisIterator(Axis.CHILD).next().getStringValue();
        
        XdmNode outputFileNode = (XdmNode) testCase.axisIterator(Axis.CHILD, OUTPUT_FILE).next();
        outputFileName = outputFileNode.axisIterator(Axis.CHILD).next().getStringValue();
        comparisonMode = ComparisonMode.valueOf(outputFileNode.getAttributeValue(COMPARE));
        
        File queryFile = new File (directory + "/Queries/XQuery/" + path + '/' + queryName + ".xq");
        queryText = IOUtils.toString (new FileInputStream(queryFile));
        
        File inputFile = new File (directory + "/TestSources/" + inputFileName + ".xml");
        inputFileText = IOUtils.toString (new FileInputStream(inputFile));
        
        File outputFile = new File (directory + "/ExpectedTestResults/" + path + '/' + outputFileName);
        outputFileText = IOUtils.toString (new FileInputStream(outputFile));
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

    public String getInputFileName() {
        return inputFileName;
    }

    public String getOutputFileName() {
        return outputFileName;
    }

    public ComparisonMode getComparisonMode() {
        return comparisonMode;
    }

    public String getInputText() {
        return inputFileText;
    }

    public String getOutputText() {
        return outputFileText;
    }

}
