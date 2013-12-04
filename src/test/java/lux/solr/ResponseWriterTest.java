package lux.solr;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;

import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.response.SolrQueryResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ResponseWriterTest extends BaseSolrTest {
    
    private LuxResponseWriter responseWriter;
    private StringWriter writer;
    
    @Before
    public void init() {
        responseWriter = new LuxResponseWriter();
        writer = new StringWriter();
    }
    
    @After
    public void exit() {
        //core.close();
    }
    
    @Test
    public void testEmptyResponse () throws Exception {
        // no error and no results; blank result?  should be throw an exception?
        assertEquals ("", writeResponse((String) null));
    }

    // return a single string with the default content type (text/html)
    @Test
    public void testStringResponse () throws Exception {
        String result = writeResponse ((String) null, "xs:string", "Hello, World");
        assertEquals ("Hello, World", result);
        assertEquals ("text/html; charset=UTF-8", responseWriter.getContentType(makeRequest(), new SolrQueryResponse()));
    }

    // return a single string with the text/xml content type
    @Test
    public void testXmlStringResponse () throws Exception {
        String result = writeResponse ("text/xml", "xs:string", "Hello, World");
        assertEquals ("<results>Hello, World</results>", result);
    }
    
    // return a single xml result with the text/xml content type
    @Test
    public void testXmlResponse () throws Exception {
        Processor processor = new Processor(false);
        XdmNode doc = processor.newDocumentBuilder().build(new StreamSource (new StringReader ("<test>Hello, World</test>")));

        String result = writeResponse ("text/xml", "element", doc);
        assertEquals ("<test>Hello, World</test>\n", result);
        
        // and with xsl
        writer = new StringWriter();
        result = writeResponse (makeRequest("lux.contentType", "text/xml", "lux.xml-xsl-stylesheet", "transform.xsl"), "element", doc);
        assertEquals ("<?xml-stylesheet type='text/xsl' href='transform.xsl' ?>\n<test>Hello, World</test>\n", result);
    }
    
    // return multiple xml results 
    @Test
    public void testMultipleXmlResults() throws Exception {
        XdmNode doc = buildDocument("<test>Hello, World</test>");
        String result = writeResponse ("text/xml", "element", doc, "element", doc);
        assertEquals ("<results><test>Hello, World</test>\n" +
        		"<test>Hello, World</test>\n" +
        		"</results>", result);
    }
    
    // if an ordinary error was caught, we report it using a SolrException so that
    // Solr can produce a 400 response
    @Test
    public void testErrorResponse() throws Exception {
        SolrQueryRequest request = makeRequest();
        SolrQueryResponse response = new SolrQueryResponse();
        response.add("xpath-error", "error #1");
        response.add("xpath-error", "error #2");
        try {
            responseWriter.write(writer, request, response);
            fail ("no exception thrown");
        } catch (SolrException e) {
            assertEquals ("error #1\nerror #2\n", e.getMessage());
        }
    }
    
    // if an internal, fatal error of some kind occurred, we generate an html response
    @Test
    public void testExceptionResponse() throws Exception {
        SolrQueryRequest request = makeRequest();
        SolrQueryResponse response = new SolrQueryResponse();
        NamedList<String> errors = new NamedList<String> ();
        // This seems to be how Solr reports errors internally?
        errors.add("msg", "An error occurred");
        response.add("error", errors);
        response.setException(new Exception (""));
        responseWriter.write(writer, request, response);
        assertEquals ("<html><head><title>Error</title></head><body><h1>Error</h1><code>An error occurred</code></body></html>", writer.getBuffer().toString());
    }
    
    private String writeResponse (SolrQueryRequest request, Object ... params) throws IOException {
        SolrQueryResponse response = new SolrQueryResponse();
        NamedList<Object> results = new NamedList<Object> ();
        for (int i = 0; i < params.length; i += 2) {
            results.add((String) params[i], params[i+1]);
        }
        response.add("xpath-results", results);
        responseWriter.write(writer, request, response);
        return writer.getBuffer().toString();
    }
    
    private String writeResponse (String contentType, Object ... params) throws IOException {
        SolrQueryRequest request;
        if (contentType != null) {
            request = makeRequest ("lux.contentType", contentType);
        } else {
            request = makeRequest ();
        }
        return writeResponse (request, params);
    }
    
    private SolrQueryRequest makeRequest(String ... params) {
        HashMap<String, String> map = new HashMap<String, String>();
        for (int i = 0; i < params.length; i+=2) {
            map.put(params[i], params[i+1]);
        }
        SolrParams solrParams = new MapSolrParams(map);
        return new SimpleSolrQuery(solrCore, solrParams);
    }
    
    private XdmNode buildDocument (String xml) throws SaxonApiException {
        Processor processor = new Processor(false);
        return processor.newDocumentBuilder().build(new StreamSource (new StringReader (xml)));
    }
    
    private static class SimpleSolrQuery extends SolrQueryRequestBase {
        SimpleSolrQuery (SolrCore core, SolrParams params) {
            super (core, params);
        }
    }

}
