package lux.solr;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;

/**
 * This component supplies a level of indirection, reading the query from a
 * location specified in the lux.xquery parameter.  When the queries are in
 * a directory, this functions like an xquery application service.
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
                URL absolutePath = new URL (path);
                String scheme = absolutePath.getProtocol();
                String contents = null;
                if (scheme.equals("lux")) {
                    // TODO retrieve app server files from lux
                } else {
                    if (absolutePath.getProtocol().equals("file")) {
                        File f = new File(absolutePath.getPath());
                        if (f.isDirectory() || ! f.canRead()) {
                            throw new SolrException (ErrorCode.FORBIDDEN, "access to " + f + " denied by rule");
                        }
                    }
                    contents = IOUtils.toString(absolutePath.openStream());   
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
