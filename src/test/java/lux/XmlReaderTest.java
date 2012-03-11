package lux;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.io.InputStreamReader;

import javax.xml.namespace.QName;

import lux.index.XmlPathMapper;
import lux.xml.JDOMBuilder;
import lux.xml.XmlReader;

import org.jdom.Document;
import org.junit.Test;

public class XmlReaderTest {

    private static final String CONTENT = "TEST &>0 This is some markup <that> is escaped ģ The end.";

    @Test public void testReadDocument () throws Exception {
        InputStream in = getClass().getClassLoader().getResourceAsStream ("lux/reader-test.xml");
        XmlReader reader = new XmlReader ();
        // build a JDOM in case we want to index XPaths
        JDOMBuilder jdomBuilder = new JDOMBuilder();
        // accumulate XML paths and QNames for indexing
        XmlPathMapper pathMapper = new XmlPathMapper();
        reader.addHandler (jdomBuilder);
        reader.addHandler (pathMapper);
        reader.read (new InputStreamReader (in));
     
        Document doc = jdomBuilder.getDocument();
        assertEquals ("test", doc.getRootElement().getName());
        assertEquals (CONTENT, normalize(doc.getRootElement().getValue()));

        // elements
        assertEquals (Integer.valueOf(1), pathMapper.getEltQNameCounts().get(new QName("title")));
        assertEquals (Integer.valueOf(2), pathMapper.getEltQNameCounts().get(new QName("entities")));
        assertEquals (Integer.valueOf(1), pathMapper.getEltQNameCounts().get(new QName("test")));
        // attributes
        assertEquals (Integer.valueOf(2), pathMapper.getAttQNameCounts().get(new QName("id")));
        // paths
        assertEquals (Integer.valueOf(1), pathMapper.getPathCounts().get("/test/@id"));
        assertEquals (Integer.valueOf(1), pathMapper.getPathCounts().get("/test/entities/@id"));
        assertEquals (Integer.valueOf(2), pathMapper.getPathCounts().get("/test/entities"));
        
        pathMapper.clear();
        assertTrue (pathMapper.getPathCounts().isEmpty());
        assertTrue (pathMapper.getEltQNameCounts().isEmpty());
        assertTrue (pathMapper.getAttQNameCounts().isEmpty());
        
    }

    private String normalize (String s) {
        return s == null ? null : s.replaceAll ("\\s+", " ").trim();
    }
 
}