package lux.solr;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;

/**
 * This servlet simply forwards all requests to /lux, with the path provided as the value of the "path" attribute.
 * It expects a SearchHandler configured with the LuxSearchComponent to be mapped to /lux in solrconfig.xml.
 * This is designed to be configured, in the container config, say webdefault.xml or in web.xml to match requests for 
 * *.xqy and to /lux/*.
 * 
 * Also: requests to /lux/*.xqy/* are forwarded in a similar way to the xquery module matching
 * /lux/*.xqy with the remainder of the path (after .xqy) passed as the value of lux.httpinfo/http/path-extra.
 */
public class LuxServlet extends HttpServlet {
    public static final String LUX_HTTPINFO = "lux.httpinfo";
    public static final String LUX_XQUERY = "lux.xquery";
    
    private String webappPath = "src/main/webapp/";
    private HashMap<String,String> mimeTypeMap;
    
    @Override
    public void init (ServletConfig config) {
        String configPath = config.getInitParameter("webappPath");
        if (configPath != null) {
            webappPath = configPath;
            if (! configPath.endsWith("/")) {
                webappPath += "/";
            }
        }
        mimeTypeMap = new HashMap<String, String>();
        mimeTypeMap.put ("jpg", "image/jpeg");
        mimeTypeMap.put ("gif", "image/gif");
        mimeTypeMap.put ("png", "image/png");
        mimeTypeMap.put ("css", "text/css");
        mimeTypeMap.put ("js", "text/javascript");
        mimeTypeMap.put ("html", "text/html");
        mimeTypeMap.put ("txt", "text/plain");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        /*
        if (! req.getRequestURI().matches(".*\\.(jpg|gif|js|css|html|txt|xqy)$")) {
            resp.sendError (HttpServletResponse.SC_NOT_FOUND);
        }
        */
        String requestUri = req.getRequestURI();
        String ext = requestUri.substring(requestUri.lastIndexOf('.') + 1);
        if (ext.equals ("xqy") || requestUri.contains(".xqy")) {
            forwardRequest(req, resp);
            return;
        }
        String mimeType = mimeTypeMap.get(ext);
        if (mimeType == null) {
            //resp.sendError (HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            mimeType = "application/octet-stream";
        }

        String path = translatePath(req);
        File f = new File(path);
        if (! f.exists()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
        resp.setContentLength((int) f.length());
        resp.setContentType(mimeType);
        FileInputStream in = new FileInputStream(f);
        IOUtils.copy(in, resp.getOutputStream());
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        forwardRequest(req, resp);
    }

    private void forwardRequest(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = translatePath(req);
        /*
        This obvious approach:
        */
        req.getRequestDispatcher("/lux").forward(new WrappedRequest(req, path),  resp);
        /*
        requires specifying that the SolrDispatchFilter is to filter FORWARD,
        even though the Solr doc says not to...
        */
    }

    private String translatePath(HttpServletRequest req) {
        final int head = "/lux/".length();
        int tail = req.getRequestURI().indexOf(".xqy");
        if (tail > 0) {
            return webappPath + req.getRequestURI().substring(head, tail + (".xqy".length()));
        } else {
            return webappPath + req.getRequestURI().substring(head);
        }
    }
    
    /**
     * Adds parameters to the request:
     * lux.xquery has the path to an xquery module
     * lux.httpinfo has the request encapsulated as an XML document
     */
    class WrappedRequest extends HttpServletRequestWrapper {

        private final Map<String,String[]> parameterMap;
        
        @SuppressWarnings("unchecked")
        public WrappedRequest(HttpServletRequest request, String xqueryPath) {
            super(request);
            this.parameterMap = new HashMap<String,String[]> (request.getParameterMap());
            parameterMap.put(LUX_XQUERY, new String[] {xqueryPath});
            parameterMap.put(LUX_HTTPINFO, new String[] {buildHttpInfo(request)});
        }
        
        @Override 
        public String getParameter (String name) {
            return parameterMap.get(name)[0];
        }
        
        @Override 
        public String[] getParameterValues (String name) {
            return parameterMap.get(name);
        }
        
        @Override
        public Map<String,String[]> getParameterMap () {
            return parameterMap;
        }
        
        // This may be a bit fragile - I worry we'll have serialization bugs -
        // but the only alternative I can see is to provide a special xquery function
        // and pass the map into the Saxon Evaluator object - but we can't get that
        // from here, and it would be thread-unsafe anyway, which is bad for a server
        private String buildHttpInfo(HttpServletRequest req) {
            StringBuilder buf = new StringBuilder();
            buf.append (String.format("<http method=\"%s\">", req.getMethod()));
            buf.append ("<parameters>");
            for (Object o : req.getParameterMap().entrySet()) {
                @SuppressWarnings("unchecked")
                Map.Entry<String, String[]> p = (Entry<String, String[]>) o;
                buf.append(String.format("<param name=\"%s\">", p.getKey()));
                for (String value : p.getValue()) {
                    buf.append(String.format ("<value>%s</value>", xmlEscape(value)));
                }
                buf.append("</param>");
            }
            buf.append ("</parameters>");
            int tail = req.getRequestURI().indexOf(".xqy");
            String pathExtra = req.getRequestURI().substring(tail + 4);
            buf.append("<path-extra>").append(xmlEscape(pathExtra)).append("</path-extra>");
            // TODO: headers, path, etc?
            buf.append ("</http>");
            return buf.toString();
        }
        
        private Object xmlEscape(String value) {
            return value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll("\"", "&quot;");
        }
    }

}
