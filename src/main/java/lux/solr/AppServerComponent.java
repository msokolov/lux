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
 * TODO: add support for controlling HTTP response via xquery;
 * eg for redirects, binary responses, etc.
 * TODO: file upload support
 */
public class AppServerComponent extends XQueryComponent {

    @Override
    public void prepare(ResponseBuilder rb) throws IOException {
        SolrQueryRequest req = rb.req;
        SolrParams params = req.getParams();            
        if (rb.getQueryString() == null) {
            String path = (String) params.get(LUX_XQUERY);
            if (! StringUtils.isBlank(path)) {
                String contents = null;
                if (path.startsWith("resource:")) {
                    String p = path.substring("resource:".length());
                    InputStream in = AppServerComponent.class.getResourceAsStream(p);
                    if (in == null) {
                        throw new SolrException (ErrorCode.NOT_FOUND, path + " not found");
                    } else {
                        try {
							contents = IOUtils.toString(in);
						} catch (IOException e) {
							LoggerFactory.getLogger(AppServerComponent.class).error("An error occurred while reading " + path, e);
						}
                        IOUtils.closeQuietly(in);
                    }
                } else {
                    // url provided with scheme
                    URL url = new URL (path);
                    String scheme = url.getProtocol();
                    if (scheme.equals("lux")) {
                        // TODO
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
                        		in = url.openStream();
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
