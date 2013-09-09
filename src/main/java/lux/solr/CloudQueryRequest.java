package lux.solr;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.search.SortSpec;

public class CloudQueryRequest extends SolrQueryRequestBase {

    private final SortSpec sortSpec;
    
    public CloudQueryRequest(SolrCore core, SolrParams params, SortSpec sortSpec) {
        super(core, params);
        this.sortSpec = sortSpec;
    }
    
    public SortSpec getSortSpec() {
        return sortSpec;
    }

}
