/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xqts;

import java.io.File;
import java.io.IOException;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XPathExecutable;
import net.sf.saxon.s9api.XPathSelector;
import net.sf.saxon.s9api.XdmNode;

/**
 * read the XQTS catalog
 * 
 * provides methods to iterate over test groups and cases, and to retrieve them by 
 * identifier.
 */
class Catalog {
    
    private Processor processor;
    private XdmNode catalog;
    private XPathCompiler compiler;
    private String directory;
    
    public Catalog (String filepath) throws SaxonApiException, IOException {
        this.directory = filepath;
        processor = new Processor (false);
        compiler = processor.newXPathCompiler();
        compiler.declareNamespace("xqts", TestCase.XQTS_NS);
        DocumentBuilder builder = processor.newDocumentBuilder();
        catalog = builder.build(new File(filepath + "/XQTSCatalog.xml"));
    }
    
    public TestCase getTestCaseByName (String name) throws SaxonApiException, IOException {
        XPathExecutable xpath = compiler.compile("//xqts:test-case[@name='" + name + "']");
        XPathSelector selector = xpath.load();
        selector.setContextItem (catalog);
        XdmNode testCaseNode = (XdmNode) selector.evaluateSingle();
        return new TestCase (testCaseNode, directory);
    }
    
}