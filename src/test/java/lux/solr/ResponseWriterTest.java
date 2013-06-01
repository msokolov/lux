package lux.solr;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;

import javax.xml.transform.stream.StreamSource;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XdmNode;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
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
        SolrQueryRequest request = makeRequest();
        SolrQueryResponse response = new SolrQueryResponse();
        responseWriter.write(writer, request, response);
        // no error and no results; blank result?  should be throw an exception?
        assertEquals ("", writer.getBuffer().toString());
    }

    // return a single string with the default content type (text/html)
    @Test
    public void testStringResponse () throws Exception {
        SolrQueryRequest request = makeRequest();
        SolrQueryResponse response = new SolrQueryResponse();
        NamedList<String> results = new NamedList<String> ();
        results.add("xs:string", "Hello, World");
        response.add("xpath-results", results);
        responseWriter.write(writer, request, response);
        assertEquals ("Hello, World", writer.getBuffer().toString());
        assertEquals ("text/html; charset=UTF-8", responseWriter.getContentType(request, response));
    }

    // return a single string with the text/xml content type
    @Test
    public void testXmlStringResponse () throws Exception {
        SolrQueryRequest request = makeRequest("lux.contentType", "text/xml");
        SolrQueryResponse response = new SolrQueryResponse();
        NamedList<String> results = new NamedList<String> ();
        results.add("xs:string", "Hello, World");
        response.add("xpath-results", results);
        responseWriter.write(writer, request, response);
        assertEquals ("<results>Hello, World</results>", writer.getBuffer().toString());
        assertEquals ("text/xml", responseWriter.getContentType(request, response));
    }
    
    // return a single xml result with the text/xml content type
    @Test
    public void testXmlResponse () throws Exception {
        SolrQueryRequest request = makeRequest("lux.contentType", "text/xml");
        SolrQueryResponse response = new SolrQueryResponse();
        NamedList<XdmNode> results = new NamedList<XdmNode> ();
        Processor processor = new Processor(false);
        XdmNode doc = processor.newDocumentBuilder().build(new StreamSource (new StringReader ("<test>Hello, World</test>")));
        results.add("element", doc);
        response.add("xpath-results", results);
        responseWriter.write(writer, request, response);
        assertEquals ("<test>Hello, World</test>\n", writer.getBuffer().toString());
        
        // and with xsl
        request = makeRequest("lux.contentType", "text/xml", "lux.xml-xsl-stylesheet", "transform.xsl");
        writer.getBuffer().setLength(0);
        responseWriter.write(writer, request, response);
        assertEquals ("<?xml-stylesheet type='text/xsl' href='transform.xsl' ?>\n<test>Hello, World</test>\n", writer.getBuffer().toString());
        
    }

    // return multiple xml results 
    @Test
    public void testMultipleXmlResults() throws Exception {
        SolrQueryRequest request = makeRequest("lux.contentType", "text/xml");
        SolrQueryResponse response = new SolrQueryResponse();
        NamedList<XdmNode> results = new NamedList<XdmNode> ();
        Processor processor = new Processor(false);
        XdmNode doc = processor.newDocumentBuilder().build(new StreamSource (new StringReader ("<test>Hello, World</test>")));
        results.add("element", doc);
        results.add("element", doc);
        response.add("xpath-results", results);
        responseWriter.write(writer, request, response);
        assertEquals ("<results><test>Hello, World</test>\n" +
        		"<test>Hello, World</test>\n" +
        		"</results>", writer.getBuffer().toString());
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
    
    private SolrQueryRequest makeRequest(String ... params) {
        HashMap<String, String> map = new HashMap<String, String>();
        for (int i = 0; i < params.length; i+=2) {
            map.put(params[i], params[i+1]);
        }
        SolrParams solrParams = new MapSolrParams(map);
        return new SolrQueryRequestBase(solrCore, solrParams) { };
    }

}
