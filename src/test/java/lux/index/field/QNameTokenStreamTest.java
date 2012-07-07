package lux.index.field;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import lux.xml.SaxonDocBuilder;
import lux.xml.XmlReader;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.junit.Test;

public class QNameTokenStreamTest {
    
    TextTokenStreamBase tokenStream;
    CharTermAttribute termAtt;
    PositionIncrementAttribute posAtt;
    OffsetAttribute offsetAtt;
    String inputString;

    // TODO: add some wide characters to the input, add some entities in attributes
    // test with newline line termination
    
    @Test
    public void testTokenStream() throws SaxonApiException, XMLStreamException, IOException {
        byte[] input = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream("lux/reader-test.xml"));
        inputString = new String (input, "utf-8");
        Processor proc = new Processor(false);
        SaxonDocBuilder builder = new SaxonDocBuilder(proc.newDocumentBuilder());
        XmlReader reader = new XmlReader();
        reader.addHandler(builder);
        reader.read(new ByteArrayInputStream(input));
        XdmNode doc = builder.getDocument();
        tokenStream = new QNameTextTokenStream(doc, builder.getTextOffsets());
        termAtt = tokenStream.addAttribute(CharTermAttribute.class);
        offsetAtt = tokenStream.addAttribute(OffsetAttribute.class);
        posAtt = tokenStream.addAttribute(PositionIncrementAttribute.class);

        // We're currently commingling text from text nodes and attributes, but ...
        // the parser doesn't provide attribute location information, and people are not used
        // to thinking of attribute values as part of the text of the document.  So we need to
        // separate out the attribute values into a different index.  Otherwise, what position
        // values can we use for them in this stream?
        // or possibly, we can reset the position after processing attributes by applying a negative
        // position increment?
        // Also - we shouldn't include attribute values  as part of their ancestor elements
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
        assertEquals (token, termAtt.toString());
        String t = inputString.substring(offsetAtt.startOffset(), offsetAtt.endOffset());
        assertEquals ("incorrect character offset", token, t);
    }
}
