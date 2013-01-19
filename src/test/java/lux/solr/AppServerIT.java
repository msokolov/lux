package lux.solr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

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
public class AppServerIT {

    private final String APP_SERVER_PATH = "http://localhost:8080";
    private final String XQUERY_PATH = "http://localhost:8983/xquery";
    private static WebClient httpclient;
    
    @Test
    public void testAppServer () throws Exception {
        String path = (APP_SERVER_PATH + "/test/test1.xqy");
        String response = httpclient.getResponse(path).getText();
        assertEquals ("<test>1 + 1 = 2</test>", response);
    }

    // TODO: move to appserver project
    @Test
    public void testServerRoot() throws Exception {
        String path = (APP_SERVER_PATH + "/");
        WebResponse response = httpclient.getResponse(path);
        assertEquals (403, response.getResponseCode());
    }
    
    @Test
    public void testSyntaxError () throws Exception {
        String path = (APP_SERVER_PATH + "/test/undeclared.xqy");
        WebResponse httpResponse = httpclient.getResponse(path);
        assertEquals (500, httpResponse.getResponseCode());
        // actually response contains an entire stack trace
        // assertEquals ("ERROR: Variable $undeclared has not been declared", httpResponse.getResponseMessage());
        String response = httpResponse.getText();
        assertTrue (httpResponse.getResponseMessage() + " does not contain expected error message", 
                httpResponse.getResponseMessage().contains ("Variable $undeclared has not been declared"));
        assertTrue (response.contains ("Variable $undeclared has not been declared"));
    }
    
    @Test
    public void testParameterMap () throws Exception {
        String path = (APP_SERVER_PATH + "/test/test-params.xqy?p1=A&p2=B&p2=C");
        String response = httpclient.getResponse(path).getText();
        // This test depends on the order in which keys are retrieved from a java.util.HashMap
        assertEquals ("<http method=\"\"><params>" +
                "<parm name=\"wt\"><value>lux</value></parm>" +
        		"<parm name=\"p2\"><value>B</value><value>C</value></parm>" +
                "<parm name=\"p1\"><value>A</value></parm>" +
        		"</params></http>", response.replaceAll("\n\\s*",""));
    }
    
    @Test
    public void testPlainText () throws Exception {
        String path = (APP_SERVER_PATH + "/test/test.txt");
        WebResponse httpResponse = httpclient.getResponse(path);
        assertEquals ("text/plain", httpResponse.getContentType());
        String response = httpResponse.getText();
        // We need to trim since HttpUnit seems to be adding an extra newline?
        assertEquals ("This is a test", response.trim());
    }
    
    /*
     * Ensure that we can write multiple documents in parallel.
     */
    @Test public void testMultiThreadedWrites () throws Exception {
        eval ("concat(lux:delete(), lux:commit(), 'OK')");
        ExecutorService taskExecutor = Executors.newFixedThreadPool(1);
        for (int i = 1; i <= 30; i++) {
            taskExecutor.execute(new TestDocInsert (i));
        }
        taskExecutor.shutdown();
        taskExecutor.awaitTermination(1, TimeUnit.SECONDS);
        eval ("lux:commit()");
        for (int i = 1; i <= 30; i++) {
            WebResponse response = eval ("doc('/test/" + i + "')");
            assertEquals (createTestDocument(i).replaceAll("\\s+", ""), response.getText().replaceAll("\\s+", ""));
        }
    }
    
    class TestDocInsert implements Runnable {
        
        final int id;
        
        TestDocInsert (int n) { id = n; }
        
        @Override public void run () {
            String insert = "let $i := lux:insert('/test/" + id + "'," + createTestDocument(id) + ") return concat('OK', $i)";
            try {
                WebResponse response = eval (insert);
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
        eval ("concat(lux:delete(), lux:commit(), 'OK')");
        ExecutorService taskExecutor = Executors.newFixedThreadPool(4);
        for (int i = 1; i <= 30; i++) {
            taskExecutor.execute(new TestDocInsertMulti (i));
        }
        taskExecutor.shutdown();
        taskExecutor.awaitTermination(5, TimeUnit.SECONDS);
        eval ("lux:commit()");
        for (int i = 1; i <= 30; i++) {
            WebResponse response = eval ("doc('/doc/" + i + "')");
            assertEquals (createTestDocument(i).replaceAll("\\s+", ""), response.getText().replaceAll("\\s+", ""));
            eval ("doc('/doc/" + i + "/0/0')");
            eval ("doc('/doc/" + i + "/1/0')");
            eval ("doc('/doc/" + i + "/1/1')");
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
                WebResponse response = eval (insert);
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

    private WebResponse eval (String xquery) throws MalformedURLException, IOException, SAXException {
        WebResponse response = httpclient.getResponse(XQUERY_PATH + "?wt=lux&q=" + xquery);
        assertEquals (200, response.getResponseCode());
        return response;
    }
    
    private String createTestDocument(int i) {
        return "<doc><title id=\"" + i + "\">" + (101-i) + "</title><test>cat</test></doc>";
    }
    
    @BeforeClass
    public static void setup () {
        httpclient = new WebConversation();
        httpclient.setExceptionsThrownOnErrorStatus(false);
    }
}
