package lux.solr;

import lux.Evaluator;
import lux.SearchIteratorBase;
import lux.functions.SearchBase.QueryParser;
import lux.index.FieldName;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.XPathException;

import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.SortSpec;

public class CloudSearchIterator extends SearchIteratorBase {
    
    private int limit; // = solr 'rows'
    private SolrQueryResponse response;
    private final String query;
    private final QueryParser queryParser;
    
    /**
     * Initialize the iterator
     * @param eval the Evaluator holds context for the query
     * @param query the Lucene query to execute and iterate
     * @param queryParser either blank (for the default qp), or 'xml' for the xml query parser  TODO: enum
     * @param sortCriteria the sort order for the results
     * @param start1 the 1-based start position at which to begin the iteration
     */
    public CloudSearchIterator (Evaluator eval, String query, QueryParser queryParser, String sortCriteria, int start1) {
        super (eval, sortCriteria, start1);
        this.limit = 20;
        this.queryParser = queryParser;
        this.query = query;
    }

    @Override
    public SequenceIterator<NodeInfo> getAnother() throws XPathException {
        return new CloudSearchIterator(eval, query, queryParser, sortCriteria, start + 1);
    }

    @Override
    public NodeInfo next() throws XPathException {
        for (;;) {
            if (response != null) {
                SolrDocumentList docs = (SolrDocumentList) response.getValues().get("response");
                if (docs == null) {
                    return null;
                }
                // FIXME: test pagination I think there is a bug here if w/start > 0?
                if (position < docs.getStart() + docs.size()) {
                    SolrDocument doc = docs.get(position++);
                    int docID = (Integer) doc.getFirstValue("docID");
                    String uri = (String) doc.getFirstValue("lux_uri");
                    Object oxml = doc.getFirstValue("lux_xml");
                    String xml = (String) ((oxml instanceof String) ? oxml : null);
                    byte [] bytes = (byte[]) ((oxml instanceof byte[]) ? oxml : null);
                    SolrQueryContext context = (SolrQueryContext) eval.getQueryContext();
                    String[] shards = context.getResponseBuilder().shards;
                    // FIXME: is this a thing?  What about docID?
                    String shard = (String) doc.getFieldValue("shard"); // ????
                    int ishard=0;
                    for (int i = 0; i < shards.length; i++) {
                        // TODO: a map for fast lookup, or perhaps a sorted array
                        if (shards[i].equals(shard)) {
                            ishard = i;
                            break;
                        }
                    }
                    // add one to shard index since "shard" 0 is reserved for internally-generated documents 
                    long shardDocID = ((ishard + 1) << 32) | docID; 
                    XdmNode node = eval.getDocReader().createXdmNode(shardDocID, uri, xml, bytes, true);
                    return node.getUnderlyingNode();
                } else if (position >= docs.getNumFound()) {
                    return null;
                }
            }
            doCloudSearch();
        }
    }
    
    /* Make a new query request, using this.query, start calculated based on the passed-in responseBuilder
    sorting based on sortCriteria, and fields=lux_xml.  Also: if rb asks for debug, pass that along
    */
    private void doCloudSearch () {
        ResponseBuilder origRB = ((SolrQueryContext)eval.getQueryContext()).getResponseBuilder();
        ModifiableSolrParams params = new ModifiableSolrParams();
        params.add((CommonParams.Q), query);
        if (QueryParser.XML == queryParser) {
            params.add("defType", "xml");
        }
        params.add(CommonParams.START, Integer.toString(position));
        params.add(CommonParams.ROWS, Integer.toString(limit));
        params.add(CommonParams.FL, eval.getCompiler().getIndexConfiguration().getFieldName(FieldName.URI) + ',' + 
                eval.getCompiler().getIndexConfiguration().getFieldName(FieldName.XML_STORE));

        SolrParams origParams = origRB.req.getParams();
        String debug = origParams.get(CommonParams.DEBUG);
        if (debug != null) {
            params.add(CommonParams.DEBUG, debug);
        }
        params.add("distrib", "true");
        params.add("shards", origParams.get("shards"));
        SolrQueryRequest req = new CloudQueryRequest(origRB.req.getCore(), params, makeSortSpec());
        XQueryComponent xqueryComponent = ((SolrQueryContext)eval.getQueryContext()).getQueryComponent();
        xqueryComponent.getSearchHandler().handleRequest(req, origRB.rsp);
        response = origRB.rsp;
    }
    
    private SortSpec makeSortSpec () {
        if (sortCriteria == null) {
            return new SortSpec (new Sort (SortField.FIELD_SCORE), start, limit);
        }
        Sort sort = makeSortFromCriteria();
        return new SortSpec (sort, start, limit);
    }
    
}
