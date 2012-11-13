package lux.solr;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

/**
 * This servlet simply forwards all requests to /lux, with the path provided as the value of the "path" attribute.
 * It expects a SearchHandler configured with the LuxSearchComponent to be mapped to /lux in solrconfig.xml.
 * This is designed to be configured, in the container config, say webdefault.xml to match requests for *.xqy .
 */
public class LuxServlet extends HttpServlet {
    public static final String LUX_HTTPINFO = "lux.httpinfo";
    public static final String LUX_XQUERY = "lux.xquery";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        forwardRequest(req, resp);
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException
    {
        forwardRequest(req, resp);
    }

    private void forwardRequest(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getRequestURI().substring("/lux/".length());
        /*
        This obvious approach:
        */
        req.getRequestDispatcher("/lux").forward(new WrappedRequest(req, path),  resp);
        /*
        requires specifying that the SolrDispatchFilter is to filter FORWARD,
        even though the Solr doc says not to...
        */
    }
    
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
        
        // This is horrible - and we'll have serialization bugs
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
            // TODO: headers, path, etc?
            buf.append ("</http>");
            return buf.toString();
        }
        
        private Object xmlEscape(String value) {
            return value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll("\"", "&quot;");
        }
    }

}
