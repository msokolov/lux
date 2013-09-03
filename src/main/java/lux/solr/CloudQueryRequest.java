package lux.solr;

import org.apache.lucene.search.Query;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.search.SortSpec;

public class CloudQueryRequest extends SolrQueryRequestBase {

    private final Query query;
    private final SortSpec sortSpec;
    
    public CloudQueryRequest(SolrCore core, SolrParams params, Query query, SortSpec sortSpec) {
        super(core, params);
        this.query = query;
        this.sortSpec = sortSpec;
    }
    
    public Query getQuery () {
        return query;
    }

    public SortSpec getSortSpec() {
        return sortSpec;
    }

}
