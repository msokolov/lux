package lux.solr;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;

import com.meterware.httpunit.WebClient;
import com.meterware.httpunit.WebConversation;

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
        String path = (APP_SERVER_PATH + "/src/test/resources/lux/test1.xqy");
        String response = httpclient.getResponse(path).getText();
        assertEquals ("<test>1 + 1 = 2</test>", response);
    }
    
    @Test
    public void testSyntaxError () throws Exception {
        String path = (APP_SERVER_PATH + "/src/test/resources/lux/undeclared.xqy");
        String response = httpclient.getResponse(path).getText();
        assertEquals ("<html><head><title>ERROR</title></head>" +
        		"<body>ERROR: Variable $undeclared has not been declared</body></html>", response);
    }
    
    @Test
    public void testParameterMap () throws Exception {
        String path = (APP_SERVER_PATH + "/src/test/resources/lux/test-params.xqy?p1=A&p2=B&p2=C");
        String response = httpclient.getResponse(path).getText();
        // This test depends on the order in which keys are retrieved from a java.util.HashMap
        assertEquals ("<http method=\"GET\"><parameters>" +
        		"<param name=\"p2\"><value>B</value><value>C</value></param>" +
                "<param name=\"p1\"><value>A</value></param>" +
        		"</parameters></http>", response.replaceAll("\n\\s*",""));
    }
    
    @BeforeClass
    public static void setup () {
        httpclient = new WebConversation();
    }
}
