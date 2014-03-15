package lux.solr;

import org.apache.solr.common.params.SolrParams;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequestBase;
import org.apache.solr.search.SortSpec;

/**
 * exposes the processing stage so it can be manipulated by SolrURIResolver and CloudSearchIterator 
 *
 */
public class CloudQueryRequest extends SolrQueryRequestBase {

    private int nextStage;
    
    private final SortSpec sortSpec;
    
    public CloudQueryRequest(SolrCore core, SolrParams params, SortSpec sortSpec) {
        super(core, params);
        this.sortSpec = sortSpec;
        this.nextStage = ResponseBuilder.STAGE_EXECUTE_QUERY;
    }
    
    public SortSpec getSortSpec() {
        return sortSpec;
    }
    
    /**
     * @return the next stage in the sharded request processing.  This is settable so we can control
     * the stage from within SolrURIResolver (which only retrieves fields) and CloudSearchIterator (which
     * uses the 2-pass query/retrieval mechanism).
     */
    public int getNextStage() {
        return nextStage;
    }
    
    public void setNextStage (int nextStage) {
        this.nextStage = nextStage;
    }

}
