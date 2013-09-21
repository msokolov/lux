package lux.xml.tinybin;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import javax.xml.transform.stream.StreamSource;

import lux.SearchTest;
import net.sf.saxon.Configuration;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.expr.sort.CodepointCollator;
import net.sf.saxon.expr.sort.GenericAtomicComparer;
import net.sf.saxon.functions.DeepEqual;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyDocumentImpl;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class TinyBinaryTest {

    Processor processor;
    DocumentBuilder builder;
    
    @Before
    public void init () {
        processor = new Processor(false);
        builder = processor.newDocumentBuilder();
    }
    
    @Test
    public void testRoundTrip() throws SaxonApiException, XPathException, IOException {
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
    public void testReadVersion0 () throws Exception {
        assertRoundTrip("lux/reader-test.xml", null, (byte) 0);
        assertRoundTrip("lux/reader-test.xml", "utf-8", (byte) 0);
    }
    
    @Test
    public void testAttributes () throws Exception {
        assertRoundTrip ("conf/solrconfig.xml", null);
        assertRoundTrip ("conf/solrconfig.xml", "utf-8");
    }
    
    @Test
    public void testEmptyAttribute() throws SaxonApiException, XPathException, IOException {
    	// this document has an attribute with an empty value
        assertRoundTrip("lux/wikipedia-ns-test.xml", "utf-8");
    }
    
    @Test
    public void testOnce() throws SaxonApiException, XPathException, IOException {
        assertRoundTrip("lux/reader-test.xml", "utf-8");
    }
    
    @Test @Ignore
    public void testBenchmark () throws IOException, SaxonApiException {
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
    
    private void assertRoundTrip (String docpath, String charsetName, byte formatVersion) throws XPathException, SaxonApiException, IOException {
    	// get a file from the class path
        InputStream in = SearchTest.class.getClassLoader().getResourceAsStream(docpath);
        // build a document from that file
        XdmNode doc = builder.build(new StreamSource(in));
        in.close();
        Charset charset = charsetName == null ? null : Charset.forName(charsetName);
        // Make a TinyBinary from the TinyTree
        TinyBinary tinyBin = new TinyBinary(((TinyDocumentImpl) doc.getUnderlyingNode()).getTree(), charset, formatVersion);
        byte[] b = tinyBin.getBytes();
        // Copy the TinyBinary using its byte array 
        TinyBinary copy = new TinyBinary (b, charset);
        Configuration config = processor.getUnderlyingConfiguration();
        // get the document node from the copy
        TinyDocumentImpl tinyDoc = copy.getTinyDocument(config);
        // for debugging:
        // processor.newSerializer(System.out).serializeNode(new XdmNode(tinyDoc));
        XPathContext context = config.getConversionContext();
        boolean equals = DeepEqual.deepEquals
            (tinyDoc.iterate(), 
             doc.getUnderlyingNode().iterate(),
             new GenericAtomicComparer (CodepointCollator.getInstance(), context),
             context,
             DeepEqual.INCLUDE_PREFIXES |
             DeepEqual.EXCLUDE_WHITESPACE_TEXT_NODES |
             DeepEqual.INCLUDE_COMMENTS |
             DeepEqual.COMPARE_STRING_VALUES |
             DeepEqual.INCLUDE_PROCESSING_INSTRUCTIONS);
        assertTrue (docpath + " was not preserved by TinyBinary roundtrip", equals);
    }
    
    private void assertRoundTrip (String docpath, String charsetName)
            throws SaxonApiException, XPathException, IOException 
    {
    	assertRoundTrip (docpath, charsetName, TinyBinary.CURRENT_FORMAT);
    }
    
}
