package lux.solr;

import lux.Evaluator;
import lux.SearchIteratorBase;
import lux.index.FieldName;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.XPathException;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.ModifiableSolrParams;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.search.SortSpec;

public class CloudSearchIterator extends SearchIteratorBase {
    
    private int offset; // = solr 'start'
    private int limit; // = solr 'rows'
    private SolrQueryResponse response;
    
    public CloudSearchIterator (Evaluator eval, Query query, String sortCriteria, int start) {
        super (eval, query, sortCriteria, start);
    }

    @Override
    public SequenceIterator<NodeInfo> getAnother() throws XPathException {
        return new CloudSearchIterator(eval, query, sortCriteria, start);
    }

    @Override
    public NodeInfo next() throws XPathException {
        for (;;) {
            if (response != null) {
                SolrDocumentList docs = (SolrDocumentList) response.getValues().get("response");
                if (position < docs.getStart() + docs.size()) {
                    SolrDocument doc = docs.get(position++);
                    int docID = (Integer) doc.getFirstValue("docID");
                    String uri = (String) doc.getFirstValue("lux_uri");
                    Object oxml = doc.getFirstValue("lux_xml");
                    String xml = (String) ((oxml instanceof String) ? oxml : null);
                    byte [] bytes = (byte[]) ((oxml instanceof byte[]) ? oxml : null);
                    XdmNode node = eval.getDocReader().createXdmNode(docID, uri, xml, bytes);
                    return node.getUnderlyingNode();
                } else if (position >= docs.getNumFound()) {
                    return null;
                }
            }
            offset = position;
            doCloudSearch();
        }
    }
    
    /* Make a new query request, using this.query, start calculated based on the passed-in responseBuilder
    sorting based on sortCriteria, and fields=lux_xml.  Also: if rb asks for debug, pass that along
    */
    private void doCloudSearch () {
        ResponseBuilder origRB = ((SolrQueryContext)eval.getQueryContext()).getResponseBuilder();
        ModifiableSolrParams params = new ModifiableSolrParams();
        // TODO: debug
        params.add("start", Integer.toString(offset));
        params.add("rows", Integer.toString(limit));
        params.add("fld", eval.getCompiler().getIndexConfiguration().getFieldName(FieldName.URI) + ',' + 
                eval.getCompiler().getIndexConfiguration().getFieldName(FieldName.XML_STORE));
        SolrQueryRequest req = new CloudQueryRequest(origRB.req.getCore(), params, query, makeSortSpec());
        XQueryComponent xqueryComponent = ((SolrQueryContext)eval.getQueryContext()).getQueryComponent();
        xqueryComponent.getSearchHandler().handleRequest(req, origRB.rsp);
        response = origRB.rsp;
    }
    
    private SortSpec makeSortSpec () {
        Sort sort = makeSortFromCriteria();
        return new SortSpec (sort, start, limit);
    }
    
}
