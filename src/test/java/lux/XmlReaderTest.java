/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.io.InputStreamReader;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;

import lux.index.XmlPathMapper;
import lux.xml.JDOMBuilder;
import lux.xml.XmlReader;

import org.jdom.Document;
import org.junit.Test;

public class XmlReaderTest {

    private static final String CONTENT = "TEST &>0 This is some markup <that> is escaped ģ The end.";
    private XmlPathMapper pathMapper;
    
    @Test public void testReadDocumentNoNS () throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream ("lux/reader-test.xml");
        Document doc = readDocument (in, false);
        
        assertEquals ("test", doc.getRootElement().getName());
        assertEquals (CONTENT, normalize(doc.getRootElement().getValue()));

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
        
        pathMapper.clear();
        assertTrue (pathMapper.getPathCounts().isEmpty());
        assertTrue (pathMapper.getEltQNameCounts().isEmpty());
        assertTrue (pathMapper.getAttQNameCounts().isEmpty());
        
    }
    
    @Test public void testReadDocumentNamespaceAware () throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream ("lux/reader-test-ns.xml");     
        Document doc = readDocument (in, true);
        assertEquals ("test", doc.getRootElement().getName());
        assertEquals (CONTENT, normalize(doc.getRootElement().getValue()));

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
    
    @Test public void testReadDocumentNamespaceUnaware () throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream ("lux/reader-test-ns.xml");
        Document doc = readDocument(in, false);
        assertEquals ("test", doc.getRootElement().getName());
        assertEquals (CONTENT, normalize(doc.getRootElement().getValue()));

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

    private Document readDocument(InputStream in, boolean namespaceAware) throws XMLStreamException {
        XmlReader reader = new XmlReader ();
        // build a JDOM in case we want to index XPaths
        JDOMBuilder jdomBuilder = new JDOMBuilder();
        // accumulate XML paths and QNames for indexing
        pathMapper = new XmlPathMapper();
        reader.addHandler (jdomBuilder);
        reader.addHandler (pathMapper);
        pathMapper.setNamespaceAware(namespaceAware);
        reader.read (new InputStreamReader (in));
     
        Document doc = jdomBuilder.getDocument();
        return doc;
    }
    
    private String normalize (String s) {
        return s == null ? null : s.replaceAll ("\\s+", " ").trim();
    }
 
}