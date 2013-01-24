package lux.solr;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;

import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.SolrQueryResponse;

public class LuxResponseWriter implements QueryResponseWriter {

    private String contentType;
    
    private Serializer serializer;
    
    public LuxResponseWriter () {
        serializer = new Serializer();
        serializer.setOutputProperty(Serializer.Property.METHOD, "html");
        serializer.setOutputProperty(Serializer.Property.ENCODING, "utf-8");
    }
    
    @Override
    public void write(Writer writer, SolrQueryRequest request, SolrQueryResponse response) throws IOException {
        @SuppressWarnings("unchecked")
        List<String> errors = response.getValues().getAll("xpath-error");
        if (! errors.isEmpty()) {
            if (errors.size() == 1) {
                throw new SolrException(ErrorCode.BAD_REQUEST, errors.get(0));
            }
            StringBuilder buf = new StringBuilder();
            for (String e : errors) {
                buf.append (e).append ("\n");
            }
            throw new SolrException (ErrorCode.BAD_REQUEST, buf.toString());
            //writeError(writer, error);
        } else {
            NamedList<?> values = (NamedList<?>) response.getValues().get("xpath-results");
            for (int i = 0; i < values.size(); i++) {
                Object val = values.getVal(i);
                if (val instanceof XdmNode) {
                    // assume text/html
                    serializer.setOutputWriter(writer);
                    try {
                        serializer.serializeNode((XdmNode) val);
                    } catch (SaxonApiException e) {
                        writeError (writer, e.getMessage());
                    }
                } else {
                    writer.write(val.toString());
                }
            }
        }
    }

    private void writeError(Writer writer, String error) throws IOException {
        writer.write(String.format("<html><head><title>ERROR</title></head><body>ERROR: %s</body></html>", error));
    }

    @Override
    public String getContentType(SolrQueryRequest request, SolrQueryResponse response) {
        return contentType;
    }

    @Override
    public void init(@SuppressWarnings("rawtypes") NamedList args) {
        if (args.get("lux.content-type") != null) {
            contentType = args.get("lux.content-type").toString();
        } else {
            contentType = "text/html; charset=UTF-8";
        }
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */