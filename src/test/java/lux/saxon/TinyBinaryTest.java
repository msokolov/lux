package lux.saxon;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.IOUtils;
import org.junit.Ignore;
import org.junit.Test;

import net.sf.saxon.Configuration;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.functions.DeepEqual;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;

import lux.SearchTest;

public class TinyBinaryTest {

    Processor processor;
    DocumentBuilder builder;
    
    @Test
    public void testRoundTrip() throws SaxonApiException, XPathException, IOException {
        processor = new Processor(false);
        builder = processor.newDocumentBuilder();
        // try building a TinyBinary and recreating a tree from that
        assertRoundTrip("lux/reader-test.xml", null);
        assertRoundTrip("lux/reader-test.xml", "utf-8");
        // reuse the same Processor (and thus namepool)        
        assertRoundTrip("lux/reader-test.xml", null);       
        assertRoundTrip("lux/reader-test.xml", "utf-8");
        // try a document that includes some namespaces
        assertRoundTrip("lux/reader-test-ns.xml", null);
        assertRoundTrip("lux/reader-test-ns.xml", "utf-8");
        // a large(r) document:
        assertRoundTrip("lux/hamlet.xml", null);
        assertRoundTrip("lux/hamlet.xml", "utf-8");
    }
    
    @Test
    public void testOnce() throws SaxonApiException, XPathException, IOException {
        processor = new Processor(false);
        builder = processor.newDocumentBuilder();
        // try building a TinyBinary and recreating a tree from that
        assertRoundTrip("lux/reader-test.xml", "utf-8");
    }
    
    @Test @Ignore
    public void testBenchmark () throws IOException, SaxonApiException {
        processor = new Processor(false);
        builder = processor.newDocumentBuilder();
        doBenchmark("lux/reader-test.xml", null, 1000);
        doBenchmark("lux/reader-test.xml", "utf-8", 1000);
        doBenchmark("lux/reader-test.xml", null, 1000);
        doBenchmark("lux/reader-test.xml", "utf-8", 1000);
        doBenchmark("lux/hamlet.xml", null, 1000);
        doBenchmark("lux/hamlet.xml", "utf-8", 1000);
    }    
    
    private void doBenchmark (String docpath, String charsetName, int iterations) throws IOException, SaxonApiException {
        InputStream in = SearchTest.class.getClassLoader().getResourceAsStream(docpath);
        byte[] inputBytes = IOUtils.toByteArray(in);
        in.close();
        Charset charset = charsetName == null ? null : Charset.forName(charsetName);
        XdmNode doc = builder.build(new StreamSource(new ByteArrayInputStream(inputBytes)));
        TinyBinary tinyBin = new TinyBinary(((TinyDocumentImpl) doc.getUnderlyingNode()).getTree(), charset);
        byte[] tinyInput = tinyBin.getBytes();
        System.out.println(String.format("Original size=%d bytes, 'tiny' binary size=%d bytes", inputBytes.length, tinyBin.length()));
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            builder.build(new StreamSource(new ByteArrayInputStream(inputBytes)));
        }
        long t1 = System.nanoTime();
        Configuration config = processor.getUnderlyingConfiguration();
        for (int i = 0; i < iterations; i++) {
            new TinyBinary(tinyInput, charset).getTinyDocument(config);
        }
        long t2 = System.nanoTime();
        System.out.println (String.format("DocBuilder: %dms; TinyBinary: %dms", (t1-start)/1000000, (t2-t1)/1000000));        
    }
    
    private void assertRoundTrip (String docpath, String charsetName)
            throws SaxonApiException, XPathException, IOException 
    {
        InputStream in = SearchTest.class.getClassLoader().getResourceAsStream(docpath);
        XdmNode doc = builder.build(new StreamSource(in));
        Charset charset = charsetName == null ? null : Charset.forName(charsetName);
        TinyBinary tinyBin = new TinyBinary(((TinyDocumentImpl) doc.getUnderlyingNode()).getTree(), charset);
        in.close();
        byte[] b = tinyBin.getBytes();
        TinyBinary copy = new TinyBinary (b, charset);
        Configuration config = processor.getUnderlyingConfiguration();
        TinyDocumentImpl tinyDoc = copy.getTinyDocument(config);
        // for debugging:
        // processor.newSerializer(System.out).serializeNode(new XdmNode(tinyDoc));
        assertTrue (DeepEqual.deepEquals(
                SingletonIterator.makeIterator(tinyDoc),
                SingletonIterator.makeIterator(doc.getUnderlyingNode()),
                new GenericAtomicComparer(CodepointCollator.getInstance(),
                        config.getConversionContext()),
                config,
                DeepEqual.INCLUDE_PREFIXES |
                DeepEqual.EXCLUDE_WHITESPACE_TEXT_NODES |
                    DeepEqual.INCLUDE_COMMENTS |
                    DeepEqual.COMPARE_STRING_VALUES |
                    DeepEqual.INCLUDE_PROCESSING_INSTRUCTIONS));
    }
}
