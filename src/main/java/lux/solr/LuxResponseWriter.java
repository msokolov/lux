package lux.solr;

import java.io.IOException;
import java.io.Writer;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.SolrQueryResponse;

public class LuxResponseWriter implements QueryResponseWriter {

    private String contentType;
    
    public void write(Writer writer, SolrQueryRequest request, SolrQueryResponse response) throws IOException {
        String error = (String) response.getValues().get("xpath-error");
        if (error != null) {
            // FIXME: HTTP 500
            writer.write(String.format("<html><head><title>ERROR</title></head><body>ERROR: %s</body></html>", error));
        } else {
            NamedList<?> values = (NamedList<?>) response.getValues().get("xpath-results");
            for (int i = 0; i < values.size(); i++) {
                writer.write(values.getVal(i).toString());
            }
        }
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
