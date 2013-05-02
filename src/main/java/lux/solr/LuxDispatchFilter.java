package lux.solr;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
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
    
    private String baseURI;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        baseURI = filterConfig.getInitParameter("base-uri");
        if (baseURI == null) {
            String path;
            if (File.separatorChar == '\\') {
                path = "///" + filterConfig.getServletContext().getRealPath("/").replace('\\', '/');
            } else {
                path = "//" + filterConfig.getServletContext().getRealPath("/");
            }
            // Create a URI since that is supposed to handle quoting of non-URI characters in the path (like spaces)
            URI uri;
            try {
                uri = new URI ("file", path, null);
            } catch (URISyntaxException e) {
                throw new ServletException ("Malformed URI for path: " + path, e);
            }
            baseURI = uri.toString();
        }
        
        // Arrange for initialization of EXPath repository by setting the appropriate system
        // property, if a path is configured using JNDI:

        String expathRepo = filterConfig.getInitParameter("org.expath.pkg.saxon.repo");
        if (expathRepo != null) {
            System.setProperty ("org.expath.pkg.saxon.repo", expathRepo);
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException {
         if( request instanceof HttpServletRequest) {
            HttpServletRequest req = (HttpServletRequest)request;
            String path = req.getServletPath();
            if (path.endsWith(".xqy") || path.contains (".xqy/")) {
                int pathOffset = path.indexOf('/', 1);
                if (pathOffset > 0) {
                    String servletPath = path.substring(0, pathOffset);
                    String xquery= path.substring(pathOffset);
                
                    Request wrapper = new Request(req);
                    wrapper.setServletPath (servletPath + "/lux");
                
                    String qs = req.getQueryString();

                    HashMap<String,String[]> params=null;
                    if (req.getMethod().equals("GET")) {
                        params = new HashMap<String, String[]>(req.getParameterMap());
                    }
                    
                    // handle URLs like /core/foo.xqy/path/info
                    int pathInfoOffset = xquery.indexOf(".xqy/");
                    if (pathInfoOffset >= 0) {
                        pathInfoOffset += ".xqy/".length();
                        String pathInfo = xquery.substring(pathInfoOffset);
                        params.put ("lux.pathInfo", new String[] { pathInfo });
                        xquery = xquery.substring(0, pathInfoOffset - 1);
                        qs = appendToQueryString(qs, "lux.pathInfo", pathInfo);
                    }
                
                    // load from classpath
                    // TODO: some way of configuring to load from db
                    String query = baseURI + xquery;
                    if (params != null) {
                    	params.put ("lux.xquery", new String[] { query });
                    	wrapper.setParameterMap(params);
                    }
                    
                    // Solr 4 actually implements its own query string parsing, so we need to
                    // swizzle the queryString here:
                    qs = appendToQueryString(qs, "lux.xquery", query);
                    wrapper.setQueryString(qs);
                    chain.doFilter(wrapper, response);
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }
    
    private String appendToQueryString (String qs, String param, String value) throws UnsupportedEncodingException {
        // swizzle the queryString here:
        if (qs == null) {
        	return param + '=' + URLEncoder.encode(value, "UTF-8");
        } else {
        	return qs + '&' + param + '=' + URLEncoder.encode(value, "utf-8");
        }
    	
    }

    @Override
    public void destroy() {

    }
    
    public class Request extends HttpServletRequestWrapper {
        
        private Map<String,String[]> parameterMap;
        
        private String pathInfo;
        
        private String servletPath;
        
        private String queryString;
        
        public Request (HttpServletRequest req) {
            super (req);
        }
        
        @Override
        public String getServletPath () {
            return servletPath;
        }
        
        @Override
        public String getPathInfo () {
            return pathInfo;
        }

        @Override 
        public Map<String,String[]> getParameterMap () {
            return parameterMap;
        }
        
        @Override
        public String getQueryString () {
        	return queryString;
        }
        
        public void setParameterMap (Map<String,String[]> map) {
            parameterMap = map;
        }
        
        public void setServletPath (String path) {
            servletPath = path;
        }
        
        public void setPathInfo (String path) {
            pathInfo = path;
        }
        
        public void setQueryString (String queryString) {
        	this.queryString = queryString;
        }
        
    }

}
