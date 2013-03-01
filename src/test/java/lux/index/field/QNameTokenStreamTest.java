package lux.index.field;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import lux.index.analysis.AttributeTokenStream;
import lux.index.analysis.DefaultAnalyzer;
import lux.index.analysis.ElementTokenStream;
import lux.index.analysis.XmlTextTokenStream;
import lux.xml.OffsetDocBuilder;
import lux.xml.Offsets;
import lux.xml.XmlReader;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XdmNode;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CharSequenceReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.junit.Test;

public class QNameTokenStreamTest {
    
    TokenStream tokenStream;
    CharTermAttribute termAtt;
    PositionIncrementAttribute posAtt;
    OffsetAttribute offsetAtt;
    String inputString;

    @Test
    public void testElementTokenStream() throws Exception {

        setup("lux/reader-test.xml", ElementTokenStream.class);
        
        assertToken("title:test", 1);
        assertToken("test:test", 0);

        assertToken("entities:0", 1);
        assertToken("test:0", 0);
        
        // check position increments for tokens in a phrase
        // also test correct offset calculation for CDATA
        for (String token : "this is some markup that is escaped".split(" ")) {
            assertToken("test:" + token, 1);
        }
        assertToken ("entities:ģé", 1);
        assertToken ("test:ģé", 0);      

        assertToken ("token:12345678", 1);
        assertToken ("test:12345678", 0);

        assertToken ("test:the", 1);
        assertToken ("test:end", 1);
        assertFalse (tokenStream.incrementToken());
    }
    
    @Test
    public void testTextTokenStream() throws Exception {
        setup("lux/reader-test.xml", XmlTextTokenStream.class);
        
        assertToken("test", 1);
        assertToken("0", 1);
        // check position increments for tokens in a phrase
        // also test correct offset calculation for CDATA
        for (String token : "this is some markup that is escaped".split(" ")) {
            assertToken(token, 1);
        }
        assertToken ("ģé", 1);
        assertToken ("12345678", 1);
        assertToken ("the", 1);
        assertToken ("end", 1);
        assertFalse (tokenStream.incrementToken());
    }
    
    @Test
    public void testAttributeTokenStream() throws Exception {
        setup("lux/reader-test.xml", AttributeTokenStream.class);
        assertTokenNoOffsets("id:test", 1);
        assertTokenNoOffsets("id:2", 1);
        assertFalse (tokenStream.incrementToken());
    }

    @Test
    public void testNoTextDocument () throws Exception {
        setup("lux/no-text.xml", AttributeTokenStream.class);
        assertTokenNoOffsets("id:1", 1);
        assertTokenNoOffsets("id:2", 1);
        assertFalse (tokenStream.incrementToken());

        setup("lux/no-text.xml", ElementTokenStream.class);
        assertFalse (tokenStream.incrementToken());

        setup("lux/no-text.xml", XmlTextTokenStream.class);
        assertFalse (tokenStream.incrementToken());
    }
    
    private void setup(String filename, Class<?> tokenStreamClass) throws Exception {
        byte[] input = IOUtils.toByteArray(getClass().getClassLoader().getResourceAsStream(filename));
        inputString = new String (input, "utf-8");
        Processor proc = new Processor(false);
        OffsetDocBuilder builder = new OffsetDocBuilder(proc);
        boolean hasCRLF = false;
        for (byte b : input) {
            if (b == '\r') {
                hasCRLF = true;
                break;
            }
        }
        builder.setFixupCRLF(hasCRLF); // TODO: should be autodetected
        XmlReader reader = new XmlReader();
        reader.addHandler(builder);
        reader.read(new ByteArrayInputStream(input));
        XdmNode doc = builder.getDocument();
        DefaultAnalyzer defaultAnalyzer = new DefaultAnalyzer();
        TokenStream textTokens = defaultAnalyzer.tokenStream("dummy", new CharSequenceReader(""));
        tokenStream = (TokenStream) tokenStreamClass.getConstructor(String.class, Analyzer.class, TokenStream.class, XdmNode.class, Offsets.class).
                newInstance("dummy", defaultAnalyzer, textTokens, doc, builder.getOffsets());
        termAtt = tokenStream.addAttribute(CharTermAttribute.class);
        offsetAtt = tokenStream.addAttribute(OffsetAttribute.class);
        posAtt = tokenStream.addAttribute(PositionIncrementAttribute.class);
        tokenStream.reset();
    }

    private void assertTokenNoOffsets(String token, int posIncr) throws IOException {
        assertTrue ("Token stream ended unexpectedly", tokenStream.incrementToken());
        assertEquals (token, termAtt.toString());
        assertEquals (posIncr, posAtt.getPositionIncrement());
    }
    
    private void assertToken(String token, int posIncr) throws IOException {
        assertTokenNoOffsets(token, posIncr);
        String t = inputString.substring(offsetAtt.startOffset(), offsetAtt.endOffset());
        String term = token.substring(token.indexOf(':') + 1);
        assertEquals ("incorrect character offset", term, normalize(t));
    }

    private Object normalize(String t) {
        // It might be nice to have a slightly more general normalization routine
        t = t.replace ("&#48;", "0");
        t = t.replace ("&#x123;", "\u0123");
        t = t.toLowerCase();
        return t;
    }
}
