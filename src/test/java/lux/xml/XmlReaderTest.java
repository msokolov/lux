package lux.xml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.util.Arrays;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import lux.index.QNameTextMapper;
import lux.index.XPathValueMapper;
import lux.index.XmlPathMapper;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.XdmNode;

import org.apache.commons.io.IOUtils;
import org.jdom.Document;
import org.junit.Test;

public class XmlReaderTest {

    private static final String CONTENT = "TEST &>0 This is some markup <that> is escaped ģ 12345678 The end.";
        
    @Test 
    public void testJDOMBuilder() throws Exception {
        JDOMBuilder jdomBuilder = new JDOMBuilder();
        handleDocument(jdomBuilder, "lux/reader-test.xml");
        Document doc = jdomBuilder.getDocument();
        
        assertEquals ("test", doc.getRootElement().getName());
        assertEquals (CONTENT, normalize(doc.getRootElement().getValue()));
    }

    @Test 
    public void testJDOMBuilderNS() throws Exception {
        JDOMBuilder jdomBuilder = new JDOMBuilder();
        handleDocument(jdomBuilder, "lux/reader-test-ns.xml");
        Document doc = jdomBuilder.getDocument();
        
        assertEquals ("test", doc.getRootElement().getName());
        assertEquals (CONTENT, normalize(doc.getRootElement().getValue()));
    }

    
    @Test
    public void testSaxonBuilder() throws Exception {
        SaxonDocBuilder saxonBuilder = new SaxonDocBuilder();
        handleDocument(saxonBuilder, "lux/reader-test.xml");
        XdmNode doc = saxonBuilder.getDocument();

        assertEquals("test", ((XdmNode) doc.axisIterator(Axis.CHILD).next()).getNodeName().toString());
        assertEquals(CONTENT, normalize(doc.getStringValue()));
    }

    @Test
    public void testSaxonBuilderNS() throws Exception {
        SaxonDocBuilder saxonBuilder = new SaxonDocBuilder();
        handleDocument(saxonBuilder, "lux/reader-test-ns.xml");
        XdmNode doc = saxonBuilder.getDocument();

        assertEquals("test", ((XdmNode) doc.axisIterator(Axis.CHILD).next()).getNodeName().toString());
        assertEquals(CONTENT, normalize(doc.getStringValue()));
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
        assertEquals (Integer.valueOf(1), pathMapper.getEltQNameCounts().get(new QName("title")));
        assertEquals (Integer.valueOf(2), pathMapper.getEltQNameCounts().get(new QName("entities")));
        assertEquals (Integer.valueOf(1), pathMapper.getEltQNameCounts().get(new QName("test")));
        // attributes
        assertEquals (Integer.valueOf(2), pathMapper.getAttQNameCounts().get(new QName("id")));
        // paths
        assertEquals (Integer.valueOf(1), pathMapper.getPathCounts().get("{} test @id"));
        assertEquals (Integer.valueOf(1), pathMapper.getPathCounts().get("{} test entities @id"));
        assertEquals (Integer.valueOf(2), pathMapper.getPathCounts().get("{} test entities"));
    }
    
    @Test 
    public void testPathMapperNS() throws Exception {
        XmlPathMapper pathMapper = new XmlPathMapper();
        assertTrue (pathMapper.isNamespaceAware());
        handleDocument(pathMapper, "lux/reader-test-ns.xml");

        // elements
        assertEquals (Integer.valueOf(1), pathMapper.getEltQNameCounts().get(new QName("http://lux.net{test}", "title")));
        assertEquals (Integer.valueOf(1), pathMapper.getEltQNameCounts().get(new QName("http://lux.net/#test", "entities")));
        assertEquals (Integer.valueOf(1), pathMapper.getEltQNameCounts().get(new QName("#2", "entities")));
        assertEquals (Integer.valueOf(1), pathMapper.getEltQNameCounts().get(new QName("http://lux.net/#test", "test")));
        // attributes
        assertEquals (Integer.valueOf(2), pathMapper.getAttQNameCounts().get(new QName("id")));
        // paths
        assertEquals (Integer.valueOf(1), pathMapper.getPathCounts().get("{} test{http%3A%2F%2Flux.net%2F%23test} @id"));
        assertEquals (Integer.valueOf(1), pathMapper.getPathCounts().get("{} test{http%3A%2F%2Flux.net%2F%23test} entities{%232} @id"));
        assertEquals (Integer.valueOf(1), pathMapper.getPathCounts().get("{} test{http%3A%2F%2Flux.net%2F%23test} entities{http%3A%2F%2Flux.net%2F%23test}"));
    }
    
    @Test 
    public void testPathMapperNSUnaware() throws Exception {
        XmlPathMapper pathMapper = new XmlPathMapper();
        pathMapper.setNamespaceAware(false);
        assertFalse (pathMapper.isNamespaceAware());
        handleDocument(pathMapper, "lux/reader-test-ns.xml");

        // elements
        assertEquals (Integer.valueOf(1), pathMapper.getEltQNameCounts().get(new QName("x:title")));
        assertEquals (Integer.valueOf(2), pathMapper.getEltQNameCounts().get(new QName("entities")));
        assertEquals (Integer.valueOf(1), pathMapper.getEltQNameCounts().get(new QName("test")));
        // attributes
        assertEquals (Integer.valueOf(2), pathMapper.getAttQNameCounts().get(new QName("id")));
        // paths
        assertEquals (Integer.valueOf(1), pathMapper.getPathCounts().get("{} test @id"));
        assertEquals (Integer.valueOf(1), pathMapper.getPathCounts().get("{} test entities @id"));
        assertEquals (Integer.valueOf(2), pathMapper.getPathCounts().get("{} test entities"));
        assertEquals (Integer.valueOf(1), pathMapper.getPathCounts().get("{} test x:title"));
    }
    
    @Test
    public void testSerializer () throws Exception {
        Serializer serializer = new Serializer();
        handleDocument(serializer, "lux/reader-test.xml");

        String xml = serializer.getDocument();
        InputStream in = getClass().getClassLoader().getResourceAsStream ("lux/reader-test-normal.xml");
        String original = IOUtils.toString(in, "UTF-8");
        assertEquals (original, xml);
    }

    @Test
    public void testSerializerNS () throws Exception {
        Serializer serializer = new Serializer();
        handleDocument(serializer, "lux/reader-test-ns.xml");

        String xml = serializer.getDocument();
        InputStream in = getClass().getClassLoader().getResourceAsStream ("lux/reader-test-ns-normal.xml");
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
        assertEquals ("{} test title TEST\0\0\0\0", String.valueOf(xpathValueMapper.getPathValues().get(1)));
        assertEquals ("{} test entities &>0\0\0\0\0\0", String.valueOf(xpathValueMapper.getPathValues().get(2)));
        assertEquals ("{} test token ȑȒȓȔȕȖȗȘ", String.valueOf(xpathValueMapper.getPathValues().get(5)));
        assertEquals ("{} test token \u0211\u0212\u0213\u0214\u0215\u0216\u0217\u0218", String.valueOf(xpathValueMapper.getPathValues().get(5)));
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
        assertEquals ("title", mapper.getNames().get(1));
        assertEquals ("TEST", mapper.getValues().get(1));
        assertEquals ("entities", mapper.getNames().get(2));
        assertEquals ("&>0", mapper.getValues().get(2));
        assertEquals ("token", mapper.getNames().get(5));
        assertEquals ("        12345678", mapper.getValues().get(5));
        assertEquals ("test", mapper.getNames().get(6));
        assertEquals ("This is some markup <that> is escaped The end.", 
                normalize (mapper.getValues().get(6)));
    }

    private void handleDocument(StAXHandler handler, String path) throws XMLStreamException {
        InputStream in = getClass().getClassLoader().getResourceAsStream (path);
        XmlReader xmlReader = new XmlReader ();
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
