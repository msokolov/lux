package lux.solr;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * rewrite URLs of the form:
 * 
 * [[app-prefix][/core-name]]/lux[/xquery-path]?query-string
 * 
 * TO
 * 
 * [[app-prefix][/core-name]]/lux?query-string&lux.xquery=[/xquery-path]
 * 
 */
public class LuxDispatchFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException {
        if( request instanceof HttpServletRequest) {
            HttpServletRequest req = (HttpServletRequest)request;
            String path = req.getServletPath();
            if (path.contains("/lux/")) {
                int pathInfoOffset = path.indexOf("/lux/") + 4;
                String servletPath = path.substring(0, pathInfoOffset);
                String pathInfo = path.substring(pathInfoOffset);
                
                Request wrapper = new Request(req);
                wrapper.setServletPath (servletPath);
                
                @SuppressWarnings("unchecked")
                HashMap<String,String[]> params = new HashMap<String, String[]>(req.getParameterMap());
                params.put ("lux.xquery", new String[] { pathInfo });
                wrapper.setParameterMap(params);
                chain.doFilter(wrapper, response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {

    }
    
    public class Request extends HttpServletRequestWrapper {
        
        private Map<String,String[]> parameterMap;
        
        private String servletPath; 
        
        public Request (HttpServletRequest req) {
            super (req);
        }
        
        @Override
        public String getServletPath () {
            return servletPath;
        }
        
        @Override
        public String getPathInfo () {
            return null;
        }

        @Override 
        public Map<String,String[]> getParameterMap () {
            return parameterMap;
        }
        
        public void setParameterMap (Map<String,String[]> map) {
            parameterMap = map;
        }
        
        public void setServletPath (String path) {
            servletPath = path;
        }
        
    }

}
