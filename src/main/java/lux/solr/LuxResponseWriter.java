package lux.solr;

import java.io.IOException;
import java.io.Writer;

import lux.api.LuxException;

import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;

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
    
    public void write(Writer writer, SolrQueryRequest request, SolrQueryResponse response) throws IOException {
        String error = (String) response.getValues().get("xpath-error");
        if (error != null) {
            throw new LuxException(error);
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

    public String getContentType(SolrQueryRequest request, SolrQueryResponse response) {
        return contentType;
    }

    public void init(@SuppressWarnings("rawtypes") NamedList args) {
        if (args.get("lux.content-type") != null) {
            contentType = args.get("lux.content-type").toString();
        } else {
            contentType = "text/html; charset=UTF-8";
        }
    }

}
