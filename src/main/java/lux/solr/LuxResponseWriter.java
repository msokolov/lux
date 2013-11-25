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

    /*
     *             net.sf.saxon.s9api.QName STATUS = new net.sf.saxon.s9api.QName("status");
            net.sf.saxon.s9api.QName MESSAGE = new net.sf.saxon.s9api.QName("message");
            String status = expathResponse.getAttributeValue(STATUS);
            if (status != null) {
            }
            String message = expathResponse.getAttributeValue(MESSAGE);
            if (message != null) {
                req.getContext().put("http:message", message);
            }

     */

    public LuxResponseWriter() {
    }

    @Override
    public void write(Writer writer, SolrQueryRequest request, SolrQueryResponse response) throws IOException {
        
        String xsl = request.getParams().get("lux.xml-xsl-stylesheet");
        @SuppressWarnings("unchecked")
        List<String> errors = response.getValues().getAll("xpath-error");
        String contentType = getContentType (request, response);
        NamedList<?> values = (NamedList<?>) response.getValues().get("xpath-results");
        if (values == null && !errors.isEmpty()) {
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
            if (values != null) {
                if (xsl != null) {
                    writer.write("<?xml-stylesheet type='text/xsl' href='" + xsl + "' ?>\n");
                    // css?
                }
                boolean wrapResults = isXML(contentType) &&
                        (values.size() == 0 || values.size() > 1 || ! errors.isEmpty() || 
                        (! (values.getName(0).equals("document") || values.getName(0).equals("element"))));
                if (wrapResults) {
                    writer.write("<results>");
                }
                for (int i = 0; i < values.size(); i++) {
                    Object val = values.getVal(i);
                    writer.write(val.toString());
                }
                if (!errors.isEmpty()) {
                    writer.write("<errors>");
                    for (String error : errors) {
                        writer.write("<error>");
                        writer.write(error.replace("&", "&amp;"). replace("<", "&lt;"));
                        writer.write("</error>");
                    }
                    writer.write("</errors>");
                }
                if (wrapResults) {
                    writer.write("</results>");
                }
            }
        }
    }
    
    private boolean isXML (String contentType) {
        return contentType.endsWith ("xml") || contentType.contains("xml; charset=");
    }

    private void writeError(Writer writer, String error) throws IOException {
        String encError = error.replace("&", "&amp;"). replace("<", "&lt;");
        writer.write(String.format ("<html><head><title>Error</title></head><body><h1>Error</h1><code>%s</code></body></html>", encError));
    }

    @Override
    public String getContentType(SolrQueryRequest request, SolrQueryResponse response) {
        String contentType = (String) request.getContext().get ("lux.contentType");
        if (contentType == null) {
            contentType= request.getParams().get("lux.contentType");
        }
        if (contentType != null) {
            return contentType;
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
