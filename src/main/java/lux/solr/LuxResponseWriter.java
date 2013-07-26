package lux.solr;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.SolrQueryResponse;

/**
 * Writes out the result of Lux evaluations
 * 
 *  lux.contentType controls the response's content-type header, and the serialization
 *  of nodes: default is html.  Output is always serialized as utf-8.
 *
 *  lux.xml-xsl-stylesheet
 */
public class LuxResponseWriter implements QueryResponseWriter {


    public LuxResponseWriter() {
    }

    @Override
    public void write(Writer writer, SolrQueryRequest request, SolrQueryResponse response) throws IOException {
        
        String xsl = request.getParams().get("lux.xml-xsl-stylesheet");
        @SuppressWarnings("unchecked")
        List<String> errors = response.getValues().getAll("xpath-error");
        String contentType= request.getParams().get("lux.contentType");
        if (!errors.isEmpty()) {
            StringBuilder buf = new StringBuilder();
            for (String e : errors) {
                buf.append(e).append("\n");
            }
            throw new SolrException(ErrorCode.BAD_REQUEST, buf.toString());
            // writeError(writer, error);
        } else if (response.getException() != null) {
            String error = (String) ((NamedList<?>) response.getValues().get("error")).get("msg");
            if (error == null) {
                error = response.getException().toString();
            }
            writeError (writer, error);
        }
        else {
            NamedList<?> values = (NamedList<?>) response.getValues().get("xpath-results");
            if (values != null) {
                if (xsl != null) {
                    writer.write("<?xml-stylesheet type='text/xsl' href='" + xsl + "' ?>\n");
                    // css?
                }
                boolean wrapResults = "text/xml".equals(contentType) && 
                        (values.size() == 0 || values.size() > 1 || 
                        (! (values.getName(0).equals("document") || values.getName(0).equals("element"))));
                if (wrapResults) {
                    writer.write("<results>");
                }
                for (int i = 0; i < values.size(); i++) {
                    Object val = values.getVal(i);
                    writer.write(val.toString());
                }
                if (wrapResults) {
                    writer.write("</results>");
                }
            }
        }
    }

    private void writeError(Writer writer, String error) throws IOException {
        String encError = error.replace("&", "&amp;"). replace("<", "&lt;");
        writer.write(String.format ("<html><head><title>Error</title></head><body><h1>Error</h1><code>%s</code></body></html>", encError));
    }

    @Override
    public String getContentType(SolrQueryRequest request, SolrQueryResponse response) {
        String contentTypeParam = request.getParams().get("lux.contentType");
        if (contentTypeParam != null) {
            return contentTypeParam;
        } else {
            return "text/html; charset=UTF-8";
        }
    }

    @Override
    public void init(@SuppressWarnings("rawtypes") NamedList args) {
    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
