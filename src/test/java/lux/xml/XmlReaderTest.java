package lux.xml;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Arrays;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.stream.StreamSource;

import lux.index.MutableString;
import lux.index.QNameTextMapper;
import lux.index.XPathValueMapper;
import lux.index.XmlPathMapper;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class XmlReaderTest {

    private static final String CONTENT = "TEST &>0 This is some markup <that> is escaped ģé 12345678 The end.";
    
    @Test
    public void testSaxonBuilder() throws Exception {
        SaxonDocBuilder saxonBuilder = new SaxonDocBuilder(new Processor(false));
        handleDocument(saxonBuilder, "lux/reader-test.xml");
        XdmNode doc = saxonBuilder.getDocument();
        assertDocContent(doc);
    }

    private void assertDocContent(XdmNode doc) {
        assertEquals("test", ((XdmNode) doc.axisIterator(Axis.CHILD).next()).getNodeName().toString());
        assertEquals(CONTENT, normalize(doc.getStringValue()));
    }
    
    @Test
    public void testSaxonBuildFromNodeImpl() throws Exception {
        SaxonDocBuilder saxonBuilder = new SaxonDocBuilder(new Processor(false));
        handleDocument(saxonBuilder, "lux/reader-test.xml");
        XdmNode doc = saxonBuilder.getDocument();
        // re-process from one node to another
        XmlReader xmlReader = new XmlReader ();
        saxonBuilder.reset();
        xmlReader.addHandler(saxonBuilder);
        xmlReader.read(doc.getUnderlyingNode());
        XdmNode doc2 = saxonBuilder.getDocument();
        assertNotSame(doc, doc2);
        assertDocContent(doc2);
    }

    @Test
    public void testSaxonBuilderNS() throws Exception {
        SaxonDocBuilder saxonBuilder = new SaxonDocBuilder(new Processor(false));
        handleDocument(saxonBuilder, "lux/reader-test-ns.xml");
        XdmNode doc = saxonBuilder.getDocument();
        assertDocContent(doc);
    }
    
    @Test 
    public void testStripNamespaces () throws Exception {
        SaxonDocBuilder saxonBuilder = new SaxonDocBuilder(new Processor(false));
        handleDocument (saxonBuilder, "lux/reader-test-ns.xml", true);
        XdmNode doc = saxonBuilder.getDocument();
        assertDocContent(doc);
        XdmNode title = (XdmNode) (doc.axisIterator(Axis.DESCENDANT, new net.sf.saxon.s9api.QName("title")).next());
        assertEquals("title", title.getNodeName().toString());
        assertEquals ("TEST", title.getStringValue());

        handleDocument (saxonBuilder, "lux/wikipedia-ns-test.xml", true);
        doc = saxonBuilder.getDocument();
        
        assertEquals ("wikipedia", doc.getStringValue());
        assertEquals ("", ((XdmNode) doc.axisIterator(Axis.CHILD).next()).getNodeName().getNamespaceURI());
    }

    @Test 
    public void testPathMapper() throws Exception {
        XmlPathMapper pathMapper = new XmlPathMapper();
        handleDocument(pathMapper, "lux/reader-test.xml");
        assertPathMapperKeys(pathMapper);
        
        pathMapper.reset();
        assertTrue (pathMapper.getPathCounts().isEmpty());
        assertTrue (pathMapper.getEltQNameCounts().isEmpty());
        assertTrue (pathMapper.getAttQNameCounts().isEmpty());
    }

    private void assertPathMapperKeys(XmlPathMapper pathMapper) {
        // elements
        assertEquals (1, pathMapper.getEltQNameCount("title"));
        assertEquals (2, pathMapper.getEltQNameCount("entities"));
        assertEquals (1, pathMapper.getEltQNameCount("test"));
        // attributes
        assertEquals (2, pathMapper.getAttQNameCount("id"));
        // paths
        assertEquals (1, pathMapper.getPathCount("{} test @id"));
        assertEquals (1, pathMapper.getPathCount("{} test entities @id"));
        assertEquals (2, pathMapper.getPathCount("{} test entities"));
    }
    
    @Test 
    public void testPathMapperNS() throws Exception {
        XmlPathMapper pathMapper = new XmlPathMapper();
        assertTrue (pathMapper.isNamespaceAware());
        handleDocument(pathMapper, "lux/reader-test-ns.xml");

        // elements
        assertEquals (1, pathMapper.getEltQNameCount("title{http://lux.net{test}}"));
        assertEquals (1, pathMapper.getEltQNameCount("entities{http://lux.net/#test}"));
        assertEquals (1, pathMapper.getEltQNameCount("entities{#2}"));
        assertEquals (1, pathMapper.getEltQNameCount("test{http://lux.net/#test}"));
        // attributes
        assertEquals (2, pathMapper.getAttQNameCount("id"));
        // paths
        assertEquals (1, pathMapper.getPathCount("{} test{http://lux.net/#test} @id"));
        assertEquals (1, pathMapper.getPathCount("{} test{http://lux.net/#test} entities{#2} @id"));
        assertEquals (1, pathMapper.getPathCount("{} test{http://lux.net/#test} entities{http://lux.net/#test}"));
    }
    
    @Test 
    public void testPathMapperNSUnaware() throws Exception {
        XmlPathMapper pathMapper = new XmlPathMapper();
        pathMapper.setNamespaceAware(false);
        assertFalse (pathMapper.isNamespaceAware());
        handleDocument(pathMapper, "lux/reader-test-ns.xml");

        // elements
        assertEquals (1, pathMapper.getEltQNameCount("x:title"));
        assertEquals (2, pathMapper.getEltQNameCount("entities"));
        assertEquals (1, pathMapper.getEltQNameCount("test"));
        // attributes
        assertEquals (2, pathMapper.getAttQNameCount("id"));
        // paths
        assertEquals (1, pathMapper.getPathCount("{} test @id"));
        assertEquals (1, pathMapper.getPathCount("{} test entities @id"));
        assertEquals (2, pathMapper.getPathCount("{} test entities"));
        assertEquals (1, pathMapper.getPathCount("{} test x:title"));
    }
    
    @Test
    public void testSerializer () throws Exception {
        Serializer serializer = new Serializer();
        handleDocument(serializer, "lux/reader-test.xml");

        assertSerialize(serializer, "lux/reader-test-norm1.xml");
    }

    @Test
    public void testSerializerNS () throws Exception {
        Serializer serializer = new Serializer();
        handleDocument(serializer, "lux/reader-test-ns.xml");

        assertSerialize(serializer, "lux/reader-test-ns-norm1.xml");
        
        handleDocument (serializer, "lux/wikipedia-ns-test.xml");
        assertSerialize(serializer, "lux/wikipedia-ns-test.xml");
    }

    private void assertSerialize(Serializer serializer, String norm) throws IOException {
        String xml = serializer.getDocument();
        InputStream in = getClass().getClassLoader().getResourceAsStream (norm);
        String original = IOUtils.toString(in, "UTF-8");
        assertEquals (original, xml);
    }
    
    @Test 
    public void testXPathValueMapper () throws Exception {
        XPathValueMapper xpathValueMapper = new XPathValueMapper();
        handleDocument (xpathValueMapper, "lux/reader-test.xml");
        assertTestPathValues(xpathValueMapper);
    }

    private void assertTestPathValues(XPathValueMapper xpathValueMapper) {
        assertEquals ("{} test @id test\0\0\0\0", String.valueOf(xpathValueMapper.getPathValues().get(0)));
        assertEquals ("{} test title TEST\0\0\0\0", String.valueOf(xpathValueMapper.getPathValues().get(2)));
        assertEquals ("{} test entities &>0\0\0\0\0\0", String.valueOf(xpathValueMapper.getPathValues().get(3)));
        assertEquals ("{} test token ȑȒȓȔȕȖȗȘ", String.valueOf(xpathValueMapper.getPathValues().get(6)));
        assertEquals ("{} test token \u0211\u0212\u0213\u0214\u0215\u0216\u0217\u0218", String.valueOf(xpathValueMapper.getPathValues().get(6)));
    }
    
    @Test
    public void testXPathValueHashString () throws Exception {
        char[] buf = new char[XPathValueMapper.HASH_SIZE];
        XPathValueMapper.hashString("        12345678".toCharArray(), buf);
        assertEquals ("\u0211\u0212\u0213\u0214\u0215\u0216\u0217\u0218", new String(buf));
        Arrays.fill(buf, '\0');
        XPathValueMapper.hashString("        !!!!!!!!".toCharArray(), buf);
        assertEquals ("\u0201\u0201\u0201\u0201\u0201\u0201\u0201\u0201", new String(buf));
        Arrays.fill(buf, '\0');
        XPathValueMapper.hashString("!!!!!!!!        ".toCharArray(), buf);
        assertEquals ("\u020f\u020f\u020f\u020f\u020f\u020f\u020f\u020f", new String(buf));
    }
    
    @Test
    public void testQNameTextMapper () throws Exception {
        QNameTextMapper mapper = new QNameTextMapper();
        handleDocument (mapper, "lux/reader-test.xml");
        assertPathMapperKeys(mapper);
        assertEquals ("@id", mapper.getNames().get(0));
        assertEquals ("test", mapper.getValues().get(0));
        assertEquals ("@att", mapper.getNames().get(1));
        // test attribute value normalization
        assertEquals ("< \t .>", mapper.getValues().get(1));
        assertEquals (new MutableString("title"), mapper.getNames().get(2));
        assertEquals ("TEST", mapper.getValues().get(2));
        assertEquals (new MutableString("entities"), mapper.getNames().get(3));
        assertEquals ("&>0", mapper.getValues().get(3));
        assertEquals (new MutableString("token"), mapper.getNames().get(6));
        assertEquals ("        12345678", mapper.getValues().get(6));
        assertEquals (new MutableString("test"), mapper.getNames().get(7));
        assertEquals ("This is some markup <that> is escaped The end.", 
                normalize (mapper.getValues().get(7).toString()));
    }
    
    public final String INPUT = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\r\n" +
            "<!DOCTYPE test PUBLIC \"test\" \"no.dtd\">\r\n" +
                    "<test xmlns=\"http://lux.net/#test\" id=\"test\">\r\n" +
                    "<!-- this is a comment -->\r\n" +
                    "<x:title xmlns:x=\"http://lux.net{test}\">TEST</x:title>\r\n" +
                    "<entities>&amp;&gt;&#48;</entities>\r\n" +
                    "<![CDATA[This is some markup <that> is escaped]]>\r\n" +
                    "<?process this ?>\r\n" +
                    "<entities xmlns=\"#2\" xmlns:y=\"#y\" y:y=\"y\" id=\"2\">&#x123;é</entities>" +
                    "<y:y xmlns:y=\"#z\" />\r\n" +
                    "<token>        12345678</token>\r\n" +
                    "  The end.\r\n" +
                    "</test>\r\n";

    /**
     * This test ensures that we correctly process namespace information when sending events
     * to the Saxon XmlStreamWriter.  At one point this failed due to lack of namespace 
     * declarations for all of the prefixes.
     * @throws SaxonApiException
     * @throws XMLStreamException
     */
    @Test 
    public void testSerialize() throws SaxonApiException, XMLStreamException {
        Processor processor = new Processor (false);
        DocumentBuilder builder = processor.newDocumentBuilder();
        SaxonDocBuilder streamBuilder = new SaxonDocBuilder(processor);
        XmlReader reader = new XmlReader();
        reader.addHandler(streamBuilder);
        InputStream testInput = getClass().getResourceAsStream("/lux/reader-test-ns.xml");
        reader.read (new InputStreamReader (testInput));
        XdmNode doc = streamBuilder.getDocument();
        net.sf.saxon.s9api.Serializer outputter = new net.sf.saxon.s9api.Serializer();
        XdmSequenceIterator iter = doc.axisIterator(Axis.DESCENDANT);
        iter.next(); // skip the root element
        while (iter.hasNext()) {
            XdmNode e = (XdmNode) iter.next();
            if (e.getNodeKind() != XdmNodeKind.ELEMENT) {
                continue;
            }
            String speech = outputter.serializeNodeToString(e);
            System.out.println (speech);
            builder.build(new StreamSource (new StringReader(speech)));
        }
    }

    private void handleDocument(StAXHandler handler, String path) throws XMLStreamException {
        handleDocument(handler, path, false);
    }
    
    private void handleDocument(StAXHandler handler, String path, boolean stripNamespaces) throws XMLStreamException {
        InputStream in = getClass().getClassLoader().getResourceAsStream (path);
        XmlReader xmlReader = new XmlReader ();
        xmlReader.setStripNamespaces(stripNamespaces);
        xmlReader.addHandler(handler);
        xmlReader.read(in);
    }

    private String normalize (String s) {
        return s == null ? null : s.replaceAll ("\\s+", " ").trim();
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
