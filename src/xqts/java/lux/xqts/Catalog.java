package lux.xqts;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.Serializer.Property;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;

/**
 * read the XQTS catalog
 * 
 * provides methods to iterate over test groups and cases, and to retrieve them by 
 * identifier.
 */
class Catalog {
    
    private Processor processor;
    private XdmNode catalogDocument;
    private XPathCompiler compiler;
    private String directory;
    private ArrayList<TestGroup> testGroups = new ArrayList<TestGroup>();
    private DocumentBuilder builder;
    private Serializer serializer;
    private HashMap<String,String> sourceFiles;
    private HashMap<String,TestCase> cases = new HashMap<String, TestCase>();
    
    static final QName TEST_SUITE = new QName(TestCase.XQTS_NS, "test-suite");
    static final QName SOURCES = new QName(TestCase.XQTS_NS, "sources");
    static final QName SOURCE  = new QName(TestCase.XQTS_NS, "source");
    static final QName ID = new QName("ID");
    static final QName FILE_NAME = new QName("FileName");
    
    public Catalog (String filepath, Processor processor) throws SaxonApiException, IOException {
        this.directory = filepath;
        this.processor = processor;
        compiler = processor.newXPathCompiler();
        compiler.declareNamespace("xqts", TestCase.XQTS_NS);
        System.out.println ("reading XQTS Catalog...");
        builder = processor.newDocumentBuilder();
        serializer = processor.newSerializer();
        serializer.setOutputProperty(Property.OMIT_XML_DECLARATION, "yes");
        serializer.setOutputProperty(Property.METHOD, "xml");
        catalogDocument = builder.build(new File(filepath + "/XQTSCatalog.xml"));
        buildSourceFiles (filepath);
        buildTestGroups ();
        System.out.println ("read " + cases.size() + " test cases");
    }
    
    private void buildSourceFiles (String filePath) throws IOException, SaxonApiException {
        XdmSequenceIterator testSuite = catalogDocument.axisIterator(Axis.CHILD, TEST_SUITE);
        XdmNode testSuiteNode = (XdmNode) testSuite.next();
        XdmSequenceIterator sources = testSuiteNode.axisIterator(Axis.CHILD, SOURCES);
        XdmNode sourcesNode = (XdmNode) sources.next();
        XdmSequenceIterator children = sourcesNode.axisIterator(Axis.CHILD, SOURCE);
        sourceFiles = new HashMap<String, String>();
        while (children.hasNext()) {
            XdmNode sourceNode = (XdmNode) children.next();
            sourceFiles.put(sourceNode.getAttributeValue(ID), filePath + '/' + sourceNode.getAttributeValue(FILE_NAME));
        }
    }

    
    private void buildTestGroups() throws IOException, SaxonApiException {
        XdmSequenceIterator testSuite = catalogDocument.axisIterator(Axis.CHILD, TEST_SUITE);
        XdmNode testSuiteNode = (XdmNode) testSuite.next();
        XdmSequenceIterator children = testSuiteNode.axisIterator(Axis.CHILD, TestGroup.TEST_GROUP);
        while (children.hasNext()) {
            XdmNode testGroupNode = (XdmNode) children.next();
            TestGroup testGroup = new TestGroup (testGroupNode, this);
            testGroups.add(testGroup);
        }
    }

    public TestCase getTestCaseByName (String name) {
        return cases.get(name);
    }
    
    public TestGroup getTestGroupByName (String name) {
        for (TestGroup testGroup : getTopTestGroups()) {
            TestGroup group = testGroup.getTestGroupByName (name);
            if (group != null)
                return group;
        }
        return null;
    }
    
    public Iterable<TestGroup> getTopTestGroups () {
        return testGroups;
    }
    
    public String getSourceFileByID (String id) {
        return sourceFiles.get(id);
    }
    
    public String getDirectory () {
        return directory;
    }

    public Processor getProcessor() {
        return processor;
    }

    public DocumentBuilder getBuilder() {
        return builder;
    }

    public Serializer getSerializer () { 
        return serializer;
    }
    
    public void putTestCase (String name, TestCase testCase) {
        cases.put(name, testCase);
    }

}/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
