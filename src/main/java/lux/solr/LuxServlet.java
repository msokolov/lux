package lux.solr;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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

        private final String xqueryPath;
        private final Map<String,Object> parameterMap;
        
        @SuppressWarnings("unchecked")
        public WrappedRequest(HttpServletRequest request, String xqueryPath) {
            super(request);
            this.xqueryPath = xqueryPath;
            this.parameterMap = new HashMap<String,Object> (request.getParameterMap());
            parameterMap.put("xquery", new String[] {xqueryPath});
        }
        
        @Override 
        public String getParameter (String name) {
            if (name.equals("xquery")) {
                return xqueryPath;
            } else {
                return super.getParameter(name);
            }
        }
        
        @Override
        public Map<String,Object> getParameterMap () {
            return parameterMap;
        }
    }

}
