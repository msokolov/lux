package lux.it;

import static org.junit.Assert.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.HttpUnitOptions;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebClient;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebResponse;

/**
 * basic test to make sure the app server is functioning - the app server can be passed a query by 
 * reference to a document, rather than as an inline query, and serializes xquery results 
 * directly as a stream, rather than wrapping them up in one of the standard solr query response 
 * structures.
 * 
 */
public class SolrIT {

    private final String TEST_SERVER_PATH = "http://localhost:8080/testapp";
    private final String APP_SERVER_PATH = "http://localhost:8080/collection1/testapp";
    private final String XQUERY_PATH = "http://localhost:8080/xquery";
    private final String LUX_PATH = "http://localhost:8080/lux/";
    private static WebClient httpclient;

    @BeforeClass
    public static void setup () {
        httpclient = new WebConversation();
        httpclient.setExceptionsThrownOnErrorStatus(false);
        HttpUnitOptions.setScriptingEnabled(false);
    }
    
    @Test
    public void testAppServer () throws Exception {
        String path = (TEST_SERVER_PATH + "?lux.xquery=lux/compiler/minus-1.xqy");
        String response = httpclient.getResponse(path).getText();
        assertEquals ("1", response);

        path = (APP_SERVER_PATH + "/lux/compiler/minus-1.xqy");
        response = httpclient.getResponse(path).getText();
        assertEquals ("1", response);
    }

    @Test
    public void testNoDirectoryListing() throws Exception {
        String path = (TEST_SERVER_PATH + "?lux.xquery=lux/");
        WebResponse response = httpclient.getResponse(path);
        assertEquals (403, response.getResponseCode());

        // FIXME? directory path not mapped to AppServerComponent which only filters *.xq*
        /*
        path = APP_SERVER_PATH + "/lux/";
        response = httpclient.getResponse(path);
        assertEquals (403, response.getResponseCode());
        */
    }
    
    @Test
    public void testSyntaxError () throws Exception {
        String path = (TEST_SERVER_PATH + "?lux.xquery=lux/functions/transform-error.xqy");
        WebResponse httpResponse = httpclient.getResponse(path);
        assertEquals (400, httpResponse.getResponseCode());
        assertEquals ("Bad Request", httpResponse.getResponseMessage());
        String response = httpResponse.getText();
        assertTrue ("Unexpected error message:\n" + response, response.contains ("The supplied file does not appear to be a stylesheet"));
    }
    
    @Test
    public void testNotFound () throws Exception {
        String path = (LUX_PATH + "notfound.xqy");
        WebResponse response = httpclient.getResponse(path);
        assertEquals (404, response.getResponseCode());
    }
    
    @Test
    public void testParameterMap () throws Exception {
        String path = (TEST_SERVER_PATH + "?lux.xquery=lux/solr/test-params.xqy&p1=A&p2=B&p2=C");
        String response = httpclient.getResponse(path).getText();
        // This test depends on the order in which keys are retrieved from a java.util.HashMap
        String expected = "<http method=\"\"><params>" +
                "<parm name=\"wt\"><value>lux</value></parm>" +
        		"<parm name=\"p2\"><value>B</value><value>C</value></parm>" +
                "<parm name=\"p1\"><value>A</value></parm>" +
        		"</params></http>";
        assertEquals (expected, response.replaceAll("\n\\s*",""));
    
        path = APP_SERVER_PATH + "/lux/solr/test-params.xqy?p1=A&p2=B&p2=C";
        response = httpclient.getResponse(path).getText();
        assertEquals (expected, response.replaceAll("\n\\s*",""));
    }
    
    @Test
    public void testExhaustResultSetMemory () throws Exception {
        String path = (TEST_SERVER_PATH + "?lux.xquery=lux/solr/huge-result.xqy");
        WebResponse httpResponse = httpclient.getResponse(path);
        httpResponse.getElementsWithName("error");
        // caught a ResourceExhaustedException
        assertTrue ("did not find expected error", httpResponse.getText().contains("Maximum result size exceeded, returned result has been truncated"));
        // some results were returned nonetheless
        assertTrue ("did not find result", httpResponse.getText().contains("abracadabra"));
        // abuse this code which means something vaguely similar
        // not right, but we can't easily influence this in Solr land
        assertEquals (httpResponse.getText(), 200, httpResponse.getResponseCode());
    }
    
    @Test
    public void testResultFormat () throws Exception {
    	verifyMultiThreadedWrites(); // load test documents
        String path = (XQUERY_PATH + "?q=subsequence(for $x in collection() order by xs:int($x//@id) return $x,1,2)&lux.contentType=text/xml&wt=lux");
        WebResponse httpResponse = httpclient.getResponse(path);
    	String expected = "<results><doc><title id=\"1\">100</title><test>cat</test></doc><doc><title id=\"2\">99</title><test>cat</test></doc></results>";
        assertEquals (expected, httpResponse.getText());
        
        path = APP_SERVER_PATH + "/lux/it/atomic-sequence.xqy?lux.contentType=text/xml";
        httpResponse = httpclient.getResponse(path);
        assertEquals (expected, httpResponse.getText());
    }
    
    @Test
    public void testAttributeEncodingInJSON () throws Exception {
    	String xmlDoc = "<doc title=\"title with &lt;tag&gt; &amp; &quot;quotes&quot; in it\" />";
    	String xmlDocEnc = URLEncoder.encode(xmlDoc, "utf-8");
    	String path = (XQUERY_PATH + "?q=" + xmlDocEnc + "&wt=json&lux.contentType=text/xml");
        WebResponse httpResponse = httpclient.getResponse(path);
        String resp = httpResponse.getText(); 
    	assertEquals ("},\"xpath-results\":[\"element\",\"<doc title=\\\"title with &lt;tag&gt; &amp; &#34;quotes&#34; in it\\\"/>\"],\"response\":{\"numFound\":0,\"start\":0,\"docs\":[]}}\n",
    			resp.substring(resp.indexOf("},")));
    }
    
    /*
     * Ensure that we can write multiple documents in parallel.
     */
    private void verifyMultiThreadedWrites () throws Exception {
        get ("concat(lux:delete('lux:/'), lux:commit(), 'OK')");
        ExecutorService taskExecutor = Executors.newFixedThreadPool(1);
        for (int i = 1; i <= 30; i++) {
            taskExecutor.execute(new TestDocInsert (i));
        }
        taskExecutor.shutdown();
        taskExecutor.awaitTermination(1, TimeUnit.SECONDS);
        get ("lux:commit()");
        for (int i = 1; i <= 30; i++) {
            WebResponse response = get ("doc('/test/" + i + "')");
            assertEquals (createTestDocument(i).replaceAll("\\s+", ""), response.getText().replaceAll("\\s+", ""));
        }
    }
    
    class TestDocInsert implements Runnable {
        
        final int id;
        
        TestDocInsert (int n) { id = n; }
        
        @Override public void run () {
            String insert = "let $i := lux:insert('/test/" + id + "'," + createTestDocument(id) + ") return concat('OK', $i)";
            try {
                WebResponse response = get (insert);
                assertEquals ("OK", response.getText());
            } catch (MalformedURLException e) {
                fail (e.getMessage());
            } catch (IOException e) {
                fail (e.getMessage());
            } catch (SAXException e) {
                fail (e.getMessage());
            }
        }
    }

    /* Now make sure that our OutputURIResolver (which handles result documents from XSLT)
     * is thread-safe.
     */
    @Test public void testMTOutputURIResolver () throws Exception {
        get ("concat(lux:delete('lux:/'), lux:commit(), 'OK')");
        long start = System.currentTimeMillis();
        ExecutorService taskExecutor = Executors.newFixedThreadPool(4);
        for (int i = 1; i <= 30; i++) {
            taskExecutor.execute(new TestDocInsertMulti (i));
        }
        taskExecutor.shutdown();
        taskExecutor.awaitTermination(5, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;
        System.out.println ("elapsed=" + elapsed);
        get ("lux:commit()");
        for (int i = 1; i <= 30; i++) {
            WebResponse response = get ("doc('/doc/" + i + "')");
            assertEquals (createTestDocument(i).replaceAll("\\s+", ""), response.getText().replaceAll("\\s+", ""));
            get ("doc('/doc/" + i + "/0/0')");
            get ("doc('/doc/" + i + "/1/0')");
            get ("doc('/doc/" + i + "/1/1')");
        }
    }
    
    class TestDocInsertMulti implements Runnable {
        
        final int id;
        
        TestDocInsertMulti (int n) { id = n; }
        
        @Override public void run () {
            String insert = "let $doc := " + createTestDocument(id) + 
                    " let $trans := lux:transform(<xsl:stylesheet version='2.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" +
                    " <xsl:template match='*'>" +
                    "   <xsl:variable name='this' select='.' />" +
                    "   <xsl:result-document href='/doc/{$doc/title/@id}/{{count($this/ancestor::*)}}/{{count($this/preceding-sibling::*)}}'><xsl:copy-of select='.'/></xsl:result-document>" +
                    "   <e><xsl:apply-templates /></e>" +
                    " </xsl:template>" +
                    "</xsl:stylesheet>, $doc)" +
                    " let $i := lux:insert('/doc/" + id + "',$doc) " +
                    " return concat('OK', $i, $trans)";
            try {
                WebResponse response = get (insert);
                assertTrue (response.getText().startsWith("OK"));
            } catch (MalformedURLException e) {
                fail (e.getMessage());
            } catch (IOException e) {
                fail (e.getMessage());
            } catch (SAXException e) {
                fail (e.getMessage());
            }
        }
    }
    
    @Test
    public void testHttpPost () throws Exception {
        WebResponse resp = post ("/lux/it/echo-params.xqy", "test", "value");
        assertEquals ("value", resp.getText());
    }

    private WebResponse get (String xquery) throws MalformedURLException, IOException, SAXException {
        WebResponse response = httpclient.getResponse(XQUERY_PATH + "?wt=lux&q=" + xquery);
        assertEquals (200, response.getResponseCode());
        return response;
    }
    
    private WebResponse post (String xquery, String ... params) throws MalformedURLException, IOException, SAXException {
        PostMethodWebRequest req = new PostMethodWebRequest(APP_SERVER_PATH + xquery);
        for (int i = 0; i < params.length; i+= 2) {
            req.setParameter(params[i], params[i+1]);
        }
        assertEquals ("", req.getQueryString());
        WebResponse response = httpclient.sendRequest(req);
        assertEquals (200, response.getResponseCode());
        return response;
    }
    
    private String createTestDocument(int i) {
        return "<doc><title id=\"" + i + "\">" + (101-i) + "</title><test>cat</test></doc>";
    }
    
}
