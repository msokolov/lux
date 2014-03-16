package lux.solr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

import lux.Evaluator;
import lux.SearchIteratorBase;
import lux.exception.LuxException;
import lux.functions.SearchBase.QueryParser;
import lux.index.FieldRole;
import lux.index.IndexConfiguration;
import net.sf.saxon.om.DocumentInfo;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.XPathException;

import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortField.Type;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.DocList;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.search.SortSpec;
import org.apache.solr.util.SolrPluginUtils;

/**
 * 
 * Perform distributed XQuery searches.  We mimic lazy evaluation by maintaining an iterator
 * that re-issues requests when its local cache is exhausted.  Note: deep paging may be quite expensive
 * since *all* results starting with the first must be retrieved for each page!  
 * 
 * TODO: We could optimize better if we kept track of per-shard positions.  To do that we'd have to know which shard each result came from.
 * This info is held in ResponseBuilder.resultIds, which is a map from id to ShardDoc; each ShardDoc has
 * a shard id.  We can count the # of docs from each shard and calculate a position from that.
 */
public class CloudSearchIterator extends SearchIteratorBase {
    
    private int limit; // = solr 'rows'
    private SolrQueryResponse response;
    private final String query;
    private final QueryParser queryParser;
    private final String xmlFieldName;
    private final String uriFieldName;
    private final String idFieldName;
    private final HashSet<String> fieldNames;
    private String[] effectiveCriteria;
    
    /**
     * Initialize the iterator
     * @param eval the Evaluator holds context for the query
     * @param query the Lucene query to execute and iterate
     * @param queryParser either blank (for the default qp), or 'xml' for the xml query parser  TODO: enum
     * @param sortCriteria the sort order for the results
     * @param start1 the 1-based start position at which to begin the iteration
     */
    public CloudSearchIterator (Evaluator eval, String query, QueryParser queryParser, String[] sortCriteria, int start1) {
        super (eval, sortCriteria, start1);
        this.limit = 20;
        this.queryParser = queryParser;
        this.query = query;
        IndexConfiguration indexConfig = eval.getCompiler().getIndexConfiguration();
        xmlFieldName = indexConfig.getFieldName(FieldRole.XML_STORE);
        uriFieldName = indexConfig.getFieldName(FieldRole.URI);
        idFieldName = indexConfig.getFieldName(FieldRole.ID);
        fieldNames = new HashSet<String>();
        fieldNames.add(xmlFieldName);
        fieldNames.add(uriFieldName);
        fieldNames.add(idFieldName);
    }

    @Override
    public SequenceIterator<NodeInfo> getAnother() throws XPathException {
        return new CloudSearchIterator(eval, query, queryParser, sortCriteria, start + 1);
    }
    
    public long count() {
        if (response == null) {
            this.limit = 0;
            doCloudSearch();
        }
        return getResultNumFound(response);
    }

    @Override
    public NodeInfo next() throws XPathException {
        for (;;) {
            if (response != null) {
                if (position >= getResultNumFound(response)) {
                    return null;
                }
                Object results = response.getValues().get("response");
                if (results == null) {
                    return null;
                }
                SolrDocumentList docs;
                if (results instanceof DocList) {
                    try {
                        docs = SolrPluginUtils.docListToSolrDocumentList ((DocList) results, 
                                (SolrIndexSearcher) eval.getSearcher().getWrappedSearcher(), fieldNames, null);
                    } catch (IOException e) {
                        throw new XPathException (e);
                    }
                }
                else if (results instanceof SolrDocumentList) {
                    docs = (SolrDocumentList) results;
                }
                else {
                    throw new XPathException ("Solr query response unexpectedly of type " + results.getClass().getName());
                }
                if (position < docs.getStart() + docs.size()) {
                    return getNextDocument (docs);
                }
                // otherwise fall through and get the next page of results
            }
            doCloudSearch();
        }
    }
    
    private NodeInfo getNextDocument (SolrDocumentList docs) {
        // FIXME: test pagination I think there is a bug here if w/start > 0?
        SolrDocument doc = docs.get(position++ - (int) docs.getStart());
        String uri = (String) doc.getFirstValue(uriFieldName);
        Object oxml = doc.getFirstValue(xmlFieldName);
        Long id = (Long) doc.getFirstValue(idFieldName);
        if (id == null) {
            // try to support migrating an old index?
            throw new LuxException("This index has no lux docids: it cannot support Lux on Solr Cloud");
        }
        String xml = (String) ((oxml instanceof String) ? oxml : null);
        byte [] bytes = (byte[]) ((oxml instanceof byte[]) ? oxml : null);
        XdmNode node = eval.getDocReader().createXdmNode(id, uri, xml, bytes);
        DocumentInfo docNode = node.getUnderlyingNode().getDocumentRoot();
        docNode.setUserData(SolrDocument.class.getName(), doc);
        return docNode;
    }

    /* Make a new query request, using this.query, start calculated based on the passed-in responseBuilder
     * sorting based on sortCriteria, and fields=lux_xml.  Also: if rb asks for debug, pass that along
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
        //params.add(CommonParams.FL, uriFieldName, xmlFieldName, idFieldName);
        params.add(CommonParams.FL, "*");

        SolrParams origParams = origRB.req.getParams();
        String debug = origParams.get(CommonParams.DEBUG);
        if (debug != null) {
            params.add(CommonParams.DEBUG, debug);
        }
        params.add("distrib", "true");
        params.add("shards", origParams.get("shards"));
        SortSpec sortSpec = makeSortSpec();
        addSortParam (params, sortSpec);
        XQueryComponent xqueryComponent = ((SolrQueryContext)eval.getQueryContext()).getQueryComponent();
        SolrQueryRequest req = new CloudQueryRequest(xqueryComponent.getCore(), params, sortSpec);
        response = new SolrQueryResponse();
        xqueryComponent.getSearchHandler().handleRequest(req, response);
        eval.getQueryStats().docCount += getResultNumFound(response); 
    }
    
    private long getResultNumFound (SolrQueryResponse rsp) {
        Object docs = response.getValues().get("response");
        if (docs != null) {
            if (docs instanceof DocList) {
                return ((DocList)docs).matches();
            } else if (docs instanceof SolrDocumentList) {
                return ((SolrDocumentList)docs).getNumFound();
            }
        }
        return 0;
    }
    
    private void addSortParam(ModifiableSolrParams params, SortSpec sortSpec) {
        for (SortField sortField : sortSpec.getSort().getSort()) {
            String dir = sortField.getReverse() ? "desc" : "asc"; 
            String field = sortField.getField();
            if (field != null) {
                params.add("sort", sortField.getField() + ' ' + dir);
            }
            // FIXME Solr controls sorting missing first/last with a *schema* setting,
            // but we insist on runtime control.  We should raise an error here
            // if the schema is not in line with the runtime setting, since otherwise
            // an incorrect ordering will be the result.  And provide some kind of 
            // "recover from the error" setting? Where?
        }
    }
    
    private String [] getEffectiveSortCriteria () {
        if (effectiveCriteria == null) {
            assert sortCriteria != null;
            ArrayList<String> tmp = new ArrayList<String>();
            for (String s : sortCriteria) {
                if (! s.equals(FieldRole.LUX_DOCID)) {
                    tmp.add(s);
                }
            }
            tmp.add(idFieldName);
            effectiveCriteria = tmp.toArray(new String[tmp.size()]);
        }
        return effectiveCriteria; 
    }

    private SortSpec makeSortSpec () {
        Sort sort;
        // add the uri field as a fallback sorting criterion to enforce a consistent
        // document order
        if (sortCriteria != null) {
            sort = makeSortFromCriteria(getEffectiveSortCriteria());
        } else {
            //sort = new Sort (SortField.FIELD_SCORE, new SortField(uriFieldName, Type.STRING));
            sort = new Sort (new SortField(idFieldName, Type.LONG));
        }
        return new SortSpec (sort, position, limit);
    }
    
    /**
     * @param limit the maximum number of results to retrieve per batch
     */
    public void setLimit (int limit) {
        this.limit = limit;
    }
    
    /**
     * @return the maximum number of results to retrieve per batch
     */
    public int getLimit () {
        return limit;
    }
    
}
