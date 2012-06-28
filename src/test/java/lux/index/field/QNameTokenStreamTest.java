package lux.index.field;

import static org.junit.Assert.*;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import lux.xml.SaxonBuilder;
import lux.xml.XmlReader;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.junit.Test;

public class QNameTokenStreamTest {

    @Test
    public void testTokenStream() throws SaxonApiException, XMLStreamException, IOException {
        Processor proc = new Processor(false);
        SaxonBuilder builder = new SaxonBuilder(proc.newDocumentBuilder());
        XmlReader reader = new XmlReader();
        reader.addHandler(builder);
        reader.read(getClass().getClassLoader().getResourceAsStream("lux/reader-test.xml"));
        XdmNode doc = builder.getDocument();
        QNameTextTokenStream tokenStream = new QNameTextTokenStream(doc);
        CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
        // OffsetAttribute offsetAtt = tokenStream.addAttribute(OffsetAttribute.class);
        PositionIncrementAttribute posAtt = tokenStream.addAttribute(PositionIncrementAttribute.class);
        
        assertTrue (tokenStream.incrementToken());
        assertEquals (1, posAtt.getPositionIncrement());
        // TODO: test offsetAtt
        assertEquals ("id:test", termAtt.toString());
        
        assertTrue (tokenStream.incrementToken());
        assertEquals (0, posAtt.getPositionIncrement());
        assertEquals ("test:test", termAtt.toString());

        assertTrue (tokenStream.incrementToken());
        assertEquals (1, posAtt.getPositionIncrement());
        assertEquals ("TEST", termAtt.toString());

        assertTrue (tokenStream.incrementToken());
        assertEquals (0, posAtt.getPositionIncrement());
        assertEquals ("title:TEST", termAtt.toString());

        assertTrue (tokenStream.incrementToken());
        assertEquals (0, posAtt.getPositionIncrement());
        assertEquals ("test:TEST", termAtt.toString());
    }
}
