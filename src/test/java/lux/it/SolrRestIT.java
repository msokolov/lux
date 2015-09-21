package lux.it;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.MalformedURLException;

import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.SAXException;

import com.meterware.httpunit.HttpUnitOptions;
import com.meterware.httpunit.PostMethodWebRequest;
import com.meterware.httpunit.WebClient;
import com.meterware.httpunit.WebConversation;
import com.meterware.httpunit.WebResponse;

/**
 * Tests inserting documents via HTTP using Solr's /update handler
 */
public class SolrRestIT {
    
    private final String SOLR_SERVER_PATH = "http://localhost:8080/collection1";
    private final String XQUERY_PATH = "http://localhost:8080/collection1/xquery";
    private static WebClient httpclient;

    @BeforeClass
    public static void setup () {
        httpclient = new WebConversation();
        httpclient.setExceptionsThrownOnErrorStatus(false);
        HttpUnitOptions.setScriptingEnabled(false);
    }
    
    private String createTestDocument(int i) {
        return "<doc><title id=\"" + i + "\">" + (101-i) + "</title><test>cat</test></doc>";
    }

    private String createAddMessage (int i) {
        return "<add><doc>" +
                "<field name='lux_xml'>" + createTestDocument (i).replace("<", "&lt;") + "</field>" +
                "<field name='lux_uri'>/doc/" + i + "</field>" + 
                "</doc></add>";
    }
    
    private WebResponse eval (String xquery) throws MalformedURLException, IOException, SAXException {
        WebResponse response = httpclient.getResponse(XQUERY_PATH + "?wt=lux&lux.contentType=text/xml&q=" + xquery);
        assertEquals (200, response.getResponseCode());
        return response;
    }
    
    /**
     * Add some documents using the Solr REST API
     * 
     * @throws Exception
     */
    @Ignore
    @Test
    public void testInsertViaHttp () throws Exception {
        String delete = "<delete><query>*:*</query></delete>";
        PostMethodWebRequest req = new PostMethodWebRequest(SOLR_SERVER_PATH + "/update", new ByteArrayInputStream(delete.getBytes("UTF-8")), "text/xml");
        WebResponse response = httpclient.getResponse(req);
        assertEquals (200, response.getResponseCode());
        
        for (int i = 0; i < 10; i++) {
            String add = createAddMessage(i);
            req = new PostMethodWebRequest(SOLR_SERVER_PATH + "/update?softCommit=true&", new ByteArrayInputStream(add.getBytes("UTF-8")), "text/xml");
            response = httpclient.getResponse(req);
            assertEquals (200, response.getResponseCode());
        }
        // get docs from transaction log (Solr's so-called "real-time get")
        response = eval ("doc('/doc/1')");
        // response = httpclient.getResponse (SOLR_SERVER_PATH + "/query?q=lux_uri:\"/doc/1\"&wt=lux");
        assertEquals (200, response.getResponseCode());
        assertEquals (createTestDocument(1), response.getText());

        // get docs from index after committing
        response = httpclient.getResponse (SOLR_SERVER_PATH + "/update?commit=true");
        response = eval ("doc('/doc/2')");
        assertEquals (200, response.getResponseCode());
        assertEquals (createTestDocument(2), response.getText());
    }
    
}
