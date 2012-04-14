package lux.solr;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import lux.XPathQuery;
import lux.api.Evaluator;
import lux.api.Expression;
import lux.api.LuxException;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.Query;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.QueryComponent;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.QueryParsing;
import org.apache.solr.search.SolrIndexSearcher;
import org.apache.solr.search.SortSpec;

/*
 * dependencies:
 * xml storage - field names
 * xml document model creation from field values - make generic
 * xpath evaluation - use evaluator
 */
public abstract class XPathSearchComponent extends QueryComponent {
    
    protected SolrIndexSearcher searcher;
    protected IndexSchema schema;
    protected Set<String> fields = new HashSet<String>();
    protected String xmlFieldName = "xml_text";
    protected Evaluator evaluator;
    
    public XPathSearchComponent() {        
        evaluator = createEvaluator();
    }
   
    public abstract Evaluator createEvaluator ();
    
    public abstract Object buildDocument (String xml);
    
    public abstract void addResult(NamedList<Object> xpathResults, Iterable<?> result);

    public void prepare(ResponseBuilder rb) throws IOException {
        // TODO: update start position since multiple xpaths could result from each document
        // TODO: cache previously-computed start position to support paging
        // TODO: skip this for queries that are 1-1 document<->result either by analysis or by fiat
        
        SolrParams params = rb.req.getParams();            
        // set the default query parser to xpath
        String defType = params.get(QueryParsing.DEFTYPE, "");
        if (defType.isEmpty()) {
            overrideParamValue(rb, QueryParsing.DEFTYPE, "xpath");
        }
        // TODO: get from config; retain fields in SearchHandler?
        fields.add(xmlFieldName);
        super.prepare(rb);
    }

    private void overrideParamValue(ResponseBuilder rb, String key, Object value) {
        NamedList<Object> tmp = rb.req.getParams().toNamedList();
        tmp.add (key, value);
        rb.req.setParams(SolrParams.toSolrParams(tmp));
    }
    
    @Override
    public void process(ResponseBuilder rb) throws IOException {
        super.process(rb);
        // process xml_text values from results as XPath...
        // TODO skip evaluation if we only asked for a count 
        // TODO OPTIMIZATION: If we know the query is minimal, and the results of the xpath 
        // are supposed to be documents, we can just reference whatever the search matched
        DocList docs = (DocList) rb.rsp.getValues().get("response");
        NamedList<Object> xpathResults = new NamedList<Object>();
        int start = rb.req.getParams().getInt(CommonParams.START, 0);
        while (docs.offset() + docs.size() > start) {
            getXPathResults (rb, xpathResults);
            if (xpathResults.size() >= rb.getQueryCommand().getLen()) {
                break;
            }
            // We need more documents            
            // 
            start += docs.size();
            overrideParamValue(rb, CommonParams.START, start);
            rb.setSortSpec(new SortSpec (rb.getSortSpec().getSort(), start, 10));
            rb.rsp.getValues().remove("response");
            // broken - not advancing??
            super.process(rb);
            docs = (DocList) rb.rsp.getValues().get("response");
        }
        rb.rsp.add("xpath-results", xpathResults);
    }

    /**
     * @param rb holds the query, documents retrieved and other stuff
     * @param xpathResults 
     * @return a list of result values; the key of each result is the type name of the result.
     * Atomic values are represented using the corresponding Java boxed types.  Elements, Documents 
     * and Text nodes are represented as serialized XML strings.  Attribute nodes are returned by value;
     * their names are lost.
     * @throws IOException 
     */
    private void getXPathResults(ResponseBuilder rb, NamedList<Object> xpathResults) throws IOException {
        Query query = rb.getQuery();
        if (! (query instanceof XPathQuery)) {
            throw new SolrException (ErrorCode.BAD_REQUEST, "XPathSearchComponent got a Query that is not an XPath");                
        }
        Expression xpath = ((XPathQuery)query).getExpression();
        DocList docs = (DocList) rb.rsp.getValues().get("response");
        int len = rb.getQueryCommand().getLen();
        DocIterator docIter = docs.iterator();
        if(searcher == null) searcher = rb.req.getSearcher();
        if(schema == null) schema = rb.req.getSchema();
        while (xpathResults.size() < len) {
            if (docIter.hasNext() == false) {
                break;
            }
            Integer id = docIter.next();
            Document doc = searcher.doc(id, fields);
            String xml = doc.get(xmlFieldName );
            try {
                Object xmlDoc = buildDocument (xml);
                getXPathResults(xpathResults, xpath, xmlDoc);
            } catch (LuxException e) {
                throw new SolrException (ErrorCode.BAD_REQUEST, e);
            }
        }
    }

    private void getXPathResults(NamedList<Object> xpathResults, Expression xpath, Object doc) throws IOException {
        Iterable<?> xpathResult = evaluator.iterate(xpath, doc); 
        addResult (xpathResults, xpathResult);        
    }
    
    public static final String COMPONENT_NAME = "xpath";

    @Override
    public String getDescription() {
        return "XPath";
    }

    @Override
    public String getSourceId() {            
        return "lux.XPathSearchComponent";
    }

    @Override
    public String getSource() {
        return "http://falutin.net/svn";
    }

    @Override
    public String getVersion() {
        return "0.1";
    }
    
}