package lux.solr;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
/**
 * This filter translates URL paths to filesystem paths by wrapping the HTTPServletRequest.  The value of 
 * parameter "servlet-path" (default: /lux) is trimmed from the beginning of request urls and replaced by 
 * the value of parameter "application-root" (which defaults to src/main/webapp - should this default value simply be "."?)
 * 
 * In addition, it identifies xquery requests (those ending ".xqy" or containing ".xqy/") and
 * provides additional parameters (lux.xquery and lux.httpinfo) for those requests.  Path translation for xquery
 * requests is handled differently; servletPath is retained, pathInfo is set to "", and path information
 * is passed to the AppServer in the "lux.xquery" parameter.
 * This induces Solr to map requests to a search handler with the same name as servletPath. 
 */
public class AppServerRequestFilter implements Filter {
    
    public static final String LUX_HTTPINFO = "lux.httpinfo";
    public static final String LUX_XQUERY = "lux.xquery";
    
    private String applicationRoot = "src/main/webapp/";
    private String servletPath = "/lux";
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        String p = filterConfig.getInitParameter("application-root");
        if (p != null) {
            applicationRoot = p;
        }
        p = filterConfig.getInitParameter("servlet-path");
        if (p != null) {
            servletPath = p;
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpReq = (HttpServletRequest) request;
            String requestUri = httpReq.getRequestURI();
            String ext = requestUri.substring(requestUri.lastIndexOf('.') + 1);
            if (ext.equals ("xqy") || requestUri.contains(".xqy/")) {
                chain.doFilter(new XQueryRequest(httpReq), response);
            } else {
                chain.doFilter(new TranslatedRequest(httpReq), response);
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
    }

    /**
     * translates URLs into file paths
     */
    class TranslatedRequest extends HttpServletRequestWrapper {
        
        private final String pathTranslated;
        private final String pathInfo;
        
        public TranslatedRequest(HttpServletRequest request) {
            super(request);
            final int head = servletPath.length();
            String requestURI = request.getRequestURI();
            pathInfo = requestURI.substring(head);
            int tail = requestURI.indexOf(".xqy");
            if (tail > 0) {
                pathTranslated = applicationRoot + requestURI.substring(head, tail + (".xqy".length()));
            } else {
                pathTranslated = applicationRoot + requestURI.substring(head);
            }
        }
        
        @Override
        public String getServletPath() {
            return "/";
        }
        
        @Override
        public String getPathInfo () {
            return pathInfo;
        }
        
        @Override
        public String getPathTranslated () {
            return pathTranslated;
        }

    }

    /**
     * Adds parameters to the request:
     * lux.xquery has the path to an xquery module
     * lux.httpinfo has the request encapsulated as an XML document
     */
    class XQueryRequest extends TranslatedRequest {
        
        private final Map<String,String[]> parameterMap;
        
        @SuppressWarnings("unchecked")
        public XQueryRequest(HttpServletRequest request) {
            super(request);
            this.parameterMap = new HashMap<String,String[]> (request.getParameterMap());
            parameterMap.put(LUX_XQUERY, new String[] {getPathTranslated()});
            parameterMap.put(LUX_HTTPINFO, new String[] {buildHttpInfo(request)});
        }
        
        @Override
        public String getServletPath() {
            return servletPath;
        }
        
        /**
         * @return "" so SolrDispatchFilter won't include this as part of the "handler" name
         */
        @Override
        public String getPathInfo () {
            return "";
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
            buf.append (String.format("<http method=\"%s\" uri=\"%s\">", req.getMethod(), xmlEscape(req.getRequestURI())));
            buf.append ("<params>");
            for (Object o : req.getParameterMap().entrySet()) {
                @SuppressWarnings("unchecked")
                Map.Entry<String, String[]> p = (Entry<String, String[]>) o;
                buf.append(String.format("<param name=\"%s\">", p.getKey()));
                for (String value : p.getValue()) {
                    buf.append(String.format ("<value>%s</value>", xmlEscape(value)));
                }
                buf.append("</param>");
            }
            buf.append ("</params>");
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

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */