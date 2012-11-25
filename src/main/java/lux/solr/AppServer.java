package lux.solr;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import lux.saxon.Evaluator;
import lux.search.LuxSearcher;
import net.sf.saxon.s9api.XdmItem;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.SolrIndexSearcher;

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
    
    /**
     * ignores start and len query parameters
     */
    @Override
    public void process(ResponseBuilder rb) throws IOException {

        SolrQueryRequest req = rb.req;
        SolrQueryResponse rsp = rb.rsp;
        SolrParams params = req.getParams();
        if (!params.getBool(COMPONENT_NAME, true)) {
          return;
        }
        SolrIndexSearcher searcher = req.getSearcher();
        SolrIndexSearcher.QueryResult result = new SolrIndexSearcher.QueryResult();
        int start = 1;
        long timeAllowed = (long)params.getInt( CommonParams.TIME_ALLOWED, -1 );
        int len = -1;
        // multiple shards not implemented
        Evaluator evaluator = new Evaluator(compiler, new LuxSearcher(searcher));
        String query = rb.getQueryString();
        if (StringUtils.isBlank(query)) {
            rsp.add("xpath-error", "query was blank");
        } else {
            evaluateQuery(rb, rsp, result, evaluator, start, timeAllowed, len, query);
        }
    }

    protected void addResult(NamedList<Object> xpathResults, XdmItem item) {
        xpathResults.add("result", item);
    }

}
