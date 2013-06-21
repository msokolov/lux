package lux.solr;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;
import org.slf4j.LoggerFactory;

/**
 * This component supplies a level of indirection, reading the query from a
 * location specified in the lux.xquery parameter.  When the queries are in
 * a directory, this functions like an xquery application service.
 * 
 * TODO: merge w/XQueryComponent.  The only distinction is that one gets its query from the "q"
 * parameter and interprets it as the query body, and the other gets the location of a query document
 * from the "lux.xquery" parameter.  Having two separate components causes us to create two Compilers,
 * two Processors, etc.
 * 
 * TODO: add extended support for accessing HTTP request and controlling HTTP response via xquery;
 * eg for redirects, binary responses, file upload, etc.
 */
public class AppServerComponent extends XQueryComponent {

    private static final String RESOURCE_SCHEME = "resource:";
    private static final String CONTEXT_SCHEME = "context:";

    @Override
    public void prepare(ResponseBuilder rb) throws IOException {
        SolrQueryRequest req = rb.req;
        SolrParams params = req.getParams();            
        if (rb.getQueryString() == null) {
            queryPath = rb.req.getParams().get(LUX_XQUERY);
            if (! StringUtils.isBlank(queryPath)) {
                String baseUri;
                String contextBase = (String) params.get("lux.serverBaseUri");
                if (params.get("lux.baseUri") != null) {
                    baseUri = (String) params.get("lux.baseUri");
                } else if (params.get("lux.serverBaseUri") != null) {
                    baseUri = contextBase;
                } else {
                    baseUri = "";
                }
                if (File.separatorChar == '\\') {
                    baseUri = baseUri.replace('\\', '/');
                }
                if (! baseUri.endsWith("/")) {
                    // add trailing slash
                    baseUri = baseUri + '/';
                }
                if (baseUri.startsWith("/") || (File.separatorChar == '\\' && baseUri.matches("^[A-Za-z]:/.*$"))) {
                    baseUri = "file://" + baseUri;
                }
                System.out.println ("BASE URI = " + baseUri);
                String resourceBase=null;
                if (baseUri.startsWith (RESOURCE_SCHEME)) {
                    resourceBase = baseUri.substring(RESOURCE_SCHEME.length());
                } else if (baseUri.startsWith(CONTEXT_SCHEME)) {
                	baseUri = contextBase + baseUri.substring(CONTEXT_SCHEME.length());
                }
                String contents = null;
                if (resourceBase != null) {
                    InputStream in = AppServerComponent.class.getResourceAsStream(resourceBase + queryPath);
                	queryPath = baseUri + queryPath;
                    if (in == null) {
                        throw new SolrException (ErrorCode.NOT_FOUND, queryPath + " not found");
                    } else {
                        try {
                            contents = IOUtils.toString(in);
                        } catch (IOException e) {
                            LoggerFactory.getLogger(AppServerComponent.class).error("An error occurred while reading " + queryPath, e);
                        }
                        IOUtils.closeQuietly(in);
                    }
                } else {
                    // url provided with scheme
                	queryPath = baseUri + queryPath;
                    URL url = new URL (queryPath);
                    String scheme = url.getProtocol();
                    if (scheme.equals("lux")) {
                        // TODO
                    	throw new SolrException (ErrorCode.NOT_FOUND, queryPath + " not found (actually lux: scheme is not implemented)");
                    } else {
                        InputStream in = null;
                        try {
                            if (url.getProtocol().equals("file")) {
                                File f = new File(url.getPath());
                                if (!f.exists()) {
                                    throw new SolrException (ErrorCode.NOT_FOUND, f + " not found");
                                }
                                if (f.isDirectory() || ! f.canRead()) {
                                    throw new SolrException (ErrorCode.FORBIDDEN, "access to " + f + " denied by rule");
                                }
                                in = new FileInputStream(f);
                            } else {
                                // in = url.openStream();
                                LoggerFactory.getLogger(AppServerComponent.class).error("URL scheme not supported: " + url.getProtocol());
                            }
                            contents = IOUtils.toString(in);
                        } catch (IOException e) {
                        	LoggerFactory.getLogger(AppServerComponent.class).error("An error occurred while reading " + url, e);
                        }
                        if (in != null) {
                            IOUtils.closeQuietly(in);
                        }
                    }
                }
                rb.setQueryString(contents);
            }
        }
        super.prepare(rb);
    }
    
    /**
     * ignores start and len query parameters
     */
    @Override
    public void process(ResponseBuilder rb) throws IOException {
        evaluateQuery(rb, -1, -1);
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
