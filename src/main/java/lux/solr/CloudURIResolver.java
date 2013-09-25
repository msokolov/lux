package lux.solr;

import javax.xml.transform.URIResolver;

import org.apache.lucene.search.Sort;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.SortSpec;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.XdmNode;

import lux.BaseURIResolver;
import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.index.field.IDField;

/**
 * Retrieves documents from distributed Solr (SolrCloud); called from fn:doc()
 * 
 * FIXME: the URIResolver gets set on the Saxon Configuration object, which is shared across
 * multiple threads.  All of XQueryComponent, SolrIndexConfig, and Processor are per-core resources,
 * and can be shared across threads.  There's no need to constantly reallocate and reset the URIResolver.
 * We can just make one and leave it there.
 * 
 * But we do need access to some query-scoped data in the resolver: namely the document builder, since
 * it tracks allocated document numbers.  Well perhaps we can allocate a new builder since the numbers
 * are persistent?
 */
public class CloudURIResolver extends BaseURIResolver {

    private final XQueryComponent xqueryComponent;
    private final String uriFieldName;
    private final String xmlFieldName;
    private final String idFieldName;
    
    CloudURIResolver(XQueryComponent xqueryComponent, URIResolver systemURIResolver) {
        super (systemURIResolver);
        this.xqueryComponent = xqueryComponent;
        IndexConfiguration indexConfig = xqueryComponent.getSolrIndexConfig().getIndexConfig();
        this.uriFieldName = indexConfig.getFieldName(FieldName.URI);
        this.xmlFieldName = indexConfig.getFieldName(FieldName.XML_STORE);
        this.idFieldName = indexConfig.getFieldName(IDField.getInstance());
    }

    @Override
    public XdmNode getDocument(String uri) {
        SolrCore core = xqueryComponent.getCore();
        SearchHandler searchHandler = xqueryComponent.getSearchHandler();
        
        // TODO refactor - copied from CloudSearchIterator.  Maybe move into XQueryComponent?
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.add((CommonParams.Q), uriFieldName + ':' + uri);
        params.add(CommonParams.FL, uriFieldName, xmlFieldName, idFieldName);
        params.add("distrib", "true");
        params.add("shards", xqueryComponent.getCurrentShards());
        SolrQueryRequest req = new CloudQueryRequest(xqueryComponent.getCore(), params, new SortSpec(Sort.INDEXORDER, 1));
        SolrQueryResponse response = new SolrQueryResponse();
        xqueryComponent.getSearchHandler().handleRequest(req, response);
        SolrDocumentList docs = (SolrDocumentList) response.getValues().get("response");
        // TODO build the document
        // We might want to use the CachingDocReader in the Evaluator created for this query by the
        // XQueryProcessor so we can share its cache, but it is a little irritating to arrange for that
        // to be available -- we would need some kind of threadlocal variable.  TODO: confirm we don't need it
        // just to preserve document identity.  This will be true as long as Saxon can handle two Objects with 
        // the same document number and treat them as equivalent.
        DocumentBuilder builder = xqueryComponent.getSolrIndexConfig().getCompiler().getProcessor().newDocumentBuilder();
        return builder.build(source);
    }
}
