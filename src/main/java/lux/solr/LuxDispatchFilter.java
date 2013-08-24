package lux.solr;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
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
 * [/core-name]/[appserver][/xquery-path]?query-string
 * 
 * TO
 * 
 * [/core-name]/[appserver]?query-string&lux.xquery=[/xquery-path]
 * 
 */
public class LuxDispatchFilter implements Filter {
    
    private String baseURI;
    private String[] baseURIArr;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        baseURI = filterConfig.getInitParameter("base-uri");
        URI uri;
        if (baseURI == null) {
            String path = filterConfig.getServletContext().getRealPath("/");
            if (path == null) {
                // unexploded war: load resources from classpath root. um.
                path = "resource:/";
            } else if (File.separatorChar == '\\') {
                path = "///" + path.replace('\\', '/');
            } else {
                path = "//" + path;
            }
            // Create a URI since that is supposed to handle quoting of non-URI characters in the path (like spaces)
            try {
                uri = new URI ("file", path, null);
            } catch (URISyntaxException e) {
                throw new ServletException ("Malformed URI for path: " + path, e);
            }
        } else {
            try {
                uri = new URI (baseURI);
            } catch (URISyntaxException e) {
                throw new ServletException ("Malformed URI for path: " + baseURI, e);
            }
        }
        baseURI = uri.toString();
        baseURIArr = new String[] { baseURI };
        
        // Arrange for initialization of EXPath repository by setting the
        // appropriate system property, if a path is configured using JNDI:

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
            if (path.contains(".xq")) {
                String [] pc = path.split("/", 4);
                if (pc.length > 2) {
                    String coreName, handlerName, xquery;
                    if (pc.length > 3) {
                        coreName = pc[1];
                        handlerName = pc[2];
                        xquery= pc[3];
                    } else {
                        coreName = "collection1"; // FIXME get default core name 
                        handlerName = pc[1];
                        xquery= pc[2];
                    }
                
                    Request wrapper = new Request(req);
                    wrapper.setServletPath ('/' + coreName + '/' + handlerName);
                
                    String qs = req.getQueryString();

                    HashMap<String,String[]> params=null;
                    if (req.getMethod().equals("GET")) {
                        @SuppressWarnings("unchecked")
                    	  Map<String, String[]> requestParams = req.getParameterMap();
                        params = new HashMap<String, String[]>(requestParams);
                    }
                    
                    // Solr 4 actually implements its own query string parsing, so we need to
                    // add our parameters to the query string in addition to setting the parameter map

                    // handle URLs like /core/foo.xqy/path/info
                    int pathInfoOffset = xquery.indexOf(".xqy/");
                    if (pathInfoOffset >= 0) {
                        pathInfoOffset += ".xqy/".length();
                        String pathInfo = xquery.substring(pathInfoOffset);
                        xquery = xquery.substring(0, pathInfoOffset - 1);
                        if (params != null) {
                            params.put ("lux.pathInfo", new String[] { pathInfo });
                        }
                        qs = appendToQueryString(qs, "lux.pathInfo", pathInfo);
                    }

                    // add lux.query and lux.base-uri to the query parameter map
                    if (params != null) {
                    	params.put ("lux.xquery", new String[] { xquery });
                    	params.put ("lux.serverBaseUri", baseURIArr);
                    }
                    qs = appendToQueryString(qs, "lux.xquery", xquery);
                    qs = appendToQueryString(qs, "lux.serverBaseUri", baseURI);

                    // set the modified query string and parameter map on a request wrapper
                    wrapper.setQueryString(qs);
                    wrapper.setParameterMap(params);

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
