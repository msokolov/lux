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
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.junit.Test;

public class QNameTokenStreamTest {
    
    QNameTextTokenStream tokenStream;
    CharTermAttribute termAtt;
    PositionIncrementAttribute posAtt;
    OffsetAttribute offsetAtt;

    @Test
    public void testTokenStream() throws SaxonApiException, XMLStreamException, IOException {
        Processor proc = new Processor(false);
        SaxonBuilder builder = new SaxonBuilder(proc.newDocumentBuilder());
        XmlReader reader = new XmlReader();
        reader.addHandler(builder);
        reader.read(getClass().getClassLoader().getResourceAsStream("lux/reader-test.xml"));
        XdmNode doc = builder.getDocument();
        tokenStream = new QNameTextTokenStream(doc);
        termAtt = tokenStream.addAttribute(CharTermAttribute.class);
        offsetAtt = tokenStream.addAttribute(OffsetAttribute.class);
        posAtt = tokenStream.addAttribute(PositionIncrementAttribute.class);

        assertToken("test", 1);
        assertToken("@id:test", 0);
        assertToken("test:test", 0);
        
        assertToken("test", 1);
        assertToken("title:test", 0);
        assertToken("test:test", 0);

        assertToken("0", 1);
        assertToken("entities:0", 0);
        assertToken("test:0", 0);
        
        // check position increments for tokens in a phrase
        for (String token : "this is some markup that is escaped".split(" ")) {
            assertToken(token, 1);
            assertToken("test:" + token, 0);
        }

    }

    private void assertToken(String token, int posIncr) throws IOException {
        assertTrue (tokenStream.incrementToken());
        assertEquals (posIncr, posAtt.getPositionIncrement());
        // TODO: test offsetAtt
        assertEquals (token, termAtt.toString());
    }
}
