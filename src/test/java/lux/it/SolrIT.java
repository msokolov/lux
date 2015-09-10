package lux.it;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.xml.transform.sax.SAXSource;

import lux.Evaluator;
import lux.QueryContext;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.trans.XPathException;
import nu.validator.htmlparser.sax.HtmlParser;

import org.apache.commons.io.IOUtils;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.InputSource;
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
@Ignore
public class SolrIT {

    private final String TEST_SERVER_PATH = "http://localhost:8080/collection1/testapp";
    private final String APP_SERVER_PATH = "http://localhost:8080/collection1/testapp";
    private final String XQUERY_PATH = "http://localhost:8080/collection1/xquery";
    private final String LUX_PATH = "http://localhost:8080/collection1/lux/";
    private static WebClient httpclient;
    private static Evaluator eval;
    
    @BeforeClass
    public static void setup () {
        httpclient = new WebConversation();
        httpclient.setExceptionsThrownOnErrorStatus(false);
        eval = new Evaluator();
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
    	assertEquals ("xpath-results\":[\"element\",\"<doc title=\\\"title with &lt;tag&gt; &amp; &#34;quotes&#34; in it\\\"/>\"]}\n",
    			resp.substring(resp.indexOf("xpath-results")));
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

        resp = post ("/lux/it/echo-request.xqy", "test", "value");

        XdmNode rspDoc = parseResponseBody(resp);
        QueryContext context = new QueryContext (rspDoc);
        assertEquals ("POST", evalString("/request/@method", context));
        assertEquals ("value", evalString("/request/param[@name='test']/@value", context));
    }

    @Test
    public void testPostXQueryServlet () throws Exception {
        String query = IOUtils.toString(getClass().getResourceAsStream("/lux/it/echo-request.xqy"));
        WebResponse resp = postToXQuery (query, "test", "value", "wt", "lux");

        XdmNode rspDoc = parseResponseBody(resp);
        QueryContext context = new QueryContext (rspDoc);
        assertEquals ("POST", evalString("/request/@method", context));
        assertEquals ("value", evalString("/request/param[@name='test']/@value", context));
    }

    private XdmNode parseResponseBody (WebResponse resp) throws IOException {
        String body = resp.getText();
        assertTrue ("empty body", body.length() > 0);
        eval.getCompiler().bindNamespacePrefix ("", "http://expath.org/ns/webapp");
        XdmNode rspDoc = eval.build(new StringReader(body), "/test.xml");
        return rspDoc;
    }

    @Test
    public void testValidatorNu() throws SaxonApiException {
        HtmlParser parser = new HtmlParser();
        Processor processor = new Processor (false);
        DocumentBuilder builder = processor.newDocumentBuilder();
        SAXSource source = new SAXSource (parser, new InputSource(new StringReader ("<!DOCTYPE html><br>")));
        builder.build(source);
        source = new SAXSource (parser, new InputSource(new StringReader ("<html>")));
        builder.build(source);
    }

    /*
     * Test cases for EXPath:
     *
     * html body
     *
     * multipart
     */
    @Test
    public void testEXPathRequest () throws Exception {
        String path = "/lux/it/echo-request.xqy";
        String qs = "a=b&a=c&c=d";
        String url = APP_SERVER_PATH + path + '?' + qs;
        WebResponse response = httpclient.getResponse(url);
        // response envelope
        assertEquals (200, response.getResponseCode());
        assertEquals ("OK", response.getResponseMessage());
        assertEquals ("application/xml+test", response.getContentType());
        assertEquals ("utf-8", response.getCharacterSet());
        XdmNode rspDoc = parseResponseBody(response);
        QueryContext context = new QueryContext(rspDoc);
        // method
        assertEquals ("GET", evalString("/request/@method", context));
        // servlet
        assertEquals ("/collection1/testapp", evalString("/request/@servlet", context));
        // path (attribute)
        assertEquals ("/collection1/testapp" + path, evalString("/request/@path", context));
        // url
        assertEquals (url, evalString("/request/url", context));
        // authority
        assertEquals ("http://localhost:8080", evalString("/request/authority", context));
        // context
        assertEquals ("", evalString ("/request/context-root", context));
        // path
        assertEquals ("/collection1/testapp" + path, evalString ("/request/path", context));
        assertEquals ("/collection1/testapp" + path, evalString ("/request/path/part", context));
        // params
        // depends on the order of hashmap keys; should be stable since String.hashCode is well-defined?
        assertEquals ("a", evalString ("(for $p in /request/param order by $p/@name return $p/@name)[1]", context));
        assertEquals ("b", evalString ("(for $p in /request/param order by $p/@name return $p/@value)[1]", context));
        assertEquals ("a", evalString ("(for $p in /request/param order by $p/@name return $p/@name)[2]", context));
        assertEquals ("c", evalString ("(for $p in /request/param order by $p/@name return $p/@value)[2]", context));
        assertEquals ("c", evalString ("(for $p in /request/param order by $p/@name return $p/@name)[3]", context));
        assertEquals ("d", evalString ("(for $p in /request/param order by $p/@name return $p/@value)[3]", context));
        // header
        assertEquals ("httpunit/1.5", evalString ("/request/header[@name='User-Agent']/@value", context));
        assertEquals ("", evalString ("/request/body", context));
    }


    @Test
    public void testPostBody () throws Exception {
        WebResponse resp = postMime ("/lux/it/echo-multipart.xqy", "<test>this is a test</test>", "text/xml");
        XdmNode rspDoc = parseResponseBody(resp);
        QueryContext context = new QueryContext (rspDoc);
        assertEquals ("POST", evalString("/result-sequence/request/@method", context));
        assertEquals ("this is a test", evalString("/result-sequence/part", context));
    }

    @Test
    public void testPostIllFormedXML () throws Exception {
        WebResponse resp = postMime ("/lux/it/echo-multipart.xqy", "<test>this is a test", "text/xml");
        XdmNode rspDoc = parseResponseBody(resp);
        QueryContext context = new QueryContext (rspDoc);
        assertEquals ("POST", evalString("/result-sequence/request/@method", context));
        assertEquals ("<test>this is a test", evalString("/result-sequence/part", context));
        assertEquals ("text/plain; charset=utf-8", evalString("/result-sequence/part/@content-type", context));
    }

    @Test
    public void testPostHTML() throws Exception {
        String html = "<!DOCTYPE html>\n<html>this is a <br>test</html>";
        WebResponse resp = postMime ("/lux/it/echo-multipart.xqy", html, "text/html");
        XdmNode rspDoc = parseResponseBody(resp);
        QueryContext context = new QueryContext (rspDoc);
        assertEquals ("POST", evalString("/result-sequence/request/@method", context));
        assertEquals ("this is a test", evalString("/result-sequence/part", context));
        assertEquals ("text/html", evalString("/result-sequence/part/@content-type", context));
    }

    private String evalString (String query, QueryContext context) throws XPathException {
        StringBuilder buf = new StringBuilder();
        for (XdmItem item : eval.evaluate (query, context).getXdmValue()) {
            buf.append (item.getStringValue());
        }
        return buf.toString();
    }

    /**
     * test cases:
     *
     * redirect (302)
     * not found (404)
     * normal (200)
     *
     * control mime-type (html, text) binary?
     *
     * @throws Exception
     */
    @Test
    public void testEXPathResponse () throws Exception {

    }

    @Test public void testRedirect () throws Exception {
        WebResponse rsp = httpclient.getResponse(APP_SERVER_PATH + "/lux/it/302.xqy");
        // http client follows the redirection:
        assertEquals (200, rsp.getResponseCode());
        XdmNode rspDoc = parseResponseBody(rsp);
        QueryContext context = new QueryContext (rspDoc);
        assertEquals ("/collection1/testapp/lux/it/echo-request.xqy" , evalString("/request/@path", context));
    }

    @Test public void test404 () throws Exception {
        WebResponse rsp = httpclient.getResponse(APP_SERVER_PATH + "/lux/it/404.xqy");
        assertEquals (404, rsp.getResponseCode());
        assertEquals ("text/html", rsp.getContentType());
        // can't create a custom 404 page *in xquery*.
        //assertEquals ("Sorry, not here, not now.", rsp.getText());
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
    
    private WebResponse postToXQuery (String xquery, String ... params) throws MalformedURLException, IOException, SAXException {
        PostMethodWebRequest req = new PostMethodWebRequest(XQUERY_PATH);
        req.setParameter ("q", xquery);
        for (int i = 0; i < params.length; i+= 2) {
            req.setParameter(params[i], params[i+1]);
        }
        assertEquals ("", req.getQueryString());
        WebResponse response = httpclient.sendRequest(req);
        assertEquals (200, response.getResponseCode());
        return response;
    }
    
    private WebResponse postMime (String xquery, String body, String contentType) throws MalformedURLException, IOException, SAXException {
        return postMime (xquery, body.getBytes("utf-8"), contentType);
    }
    
    private WebResponse postMime (String xquery, byte[] body, String contentType) throws MalformedURLException, IOException, SAXException {
        PostMethodWebRequest req = new PostMethodWebRequest(APP_SERVER_PATH + xquery, new ByteArrayInputStream(body), contentType);
        assertEquals ("", req.getQueryString());
        WebResponse response = httpclient.sendRequest(req);
        assertEquals (200, response.getResponseCode());
        return response;
    }
    
    private String createTestDocument(int i) {
        return "<doc><title id=\"" + i + "\">" + (101-i) + "</title><test>cat</test></doc>";
    }
    
}
