package lux.solr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

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

    private final String APP_SERVER_PATH = "http://localhost:8983/lux";
    private static WebClient httpclient;
    
    @Test
    public void testAppServer () throws Exception {
        String path = (APP_SERVER_PATH + "/test/test1.xqy");
        String response = httpclient.getResponse(path).getText();
        assertEquals ("<test>1 + 1 = 2</test>", response);
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
        assertEquals ("<http method=\"GET\"><params>" +
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
    
    @BeforeClass
    public static void setup () {
        httpclient = new WebConversation();
        httpclient.setExceptionsThrownOnErrorStatus(false);
    }
}
