package lux.solr;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import net.sf.saxon.s9api.XdmItem;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;

public class AppServer extends XQueryComponent {

    @Override
    public void prepare(ResponseBuilder rb) throws IOException {
        SolrQueryRequest req = rb.req;
        SolrParams params = req.getParams();            
        if (rb.getQueryString() == null) {
            String path = (String) params.get(LuxServlet.LUX_XQUERY);
            if (! StringUtils.isBlank(path)) {
                URL absolutePath = new URL (baseUri, path);
                String scheme = absolutePath.getProtocol();
                String contents = null;
                if (scheme.equals("lux")) {
                    // TODO
                } else {
                    if (absolutePath.getProtocol().equals("file")) {
                        File f = new File(absolutePath.getPath());
                        if (f.isDirectory() || ! f.canRead()) {
                            // Maybe throw an exception ?
                            // This just causes the wuery to be empty
                            return;
                        }
                    }
                    contents = IOUtils.toString(absolutePath.openStream());   
                }
                rb.setQueryString(contents);
            }
        }
    }
    
    protected void addResult(NamedList<Object> xpathResults, XdmItem item) {
        xpathResults.add("result", item);
    }

}
