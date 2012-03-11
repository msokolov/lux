package lux.solr;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import lux.Collection;
import lux.XPathQuery;
import lux.xml.JDOMBuilder;
import lux.xml.XmlReader;

import org.apache.lucene.document.Document;
import org.apache.lucene.search.Query;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.handler.component.QueryComponent;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.search.DocIterator;
import org.apache.solr.search.DocList;
import org.apache.solr.search.QueryParsing;
import org.apache.solr.search.SolrIndexSearcher;
import org.jaxen.JaxenException;
import org.jaxen.XPath;

public class XPathSearchComponent extends QueryComponent {
    
    private SolrIndexSearcher searcher;
    private IndexSchema schema;
    private Set<String> fields = new HashSet<String>();
    private String xmlFieldName = "xml_text";

    public void prepare(ResponseBuilder rb) throws IOException {
        // TODO: update start position since multiple xpaths could result from each document
        // TODO: cache previously-computed start position to support paging
        // TODO: skip this for queries that are 1-1 document<->result either by analysis or by fiat
        
        SolrParams params = rb.req.getParams();            
        // set the default query parser to xpath
        String defType = params.get(QueryParsing.DEFTYPE, "");
        
        if (defType.isEmpty()) {
            NamedList<Object> tmp = params.toNamedList();
            tmp.add(QueryParsing.DEFTYPE, "xpath");
            rb.req.setParams(SolrParams.toSolrParams(tmp));
        }
        // TODO: get from config; retain fields in SearchHandler?
        fields.add(xmlFieldName);
        super.prepare(rb);
    }
    
    @Override
    public void process(ResponseBuilder rb) throws IOException {
        super.process(rb);
        // process xml_text values from results as XPath...
        // TODO skip evaluation if we only asked for a count 
        // TODO OPTIMIZATION: If we know the query is minimal, and the results of the xpath 
        // are supposed to be documents, we can just reference whatever the search matched
        NamedList<Object> xpathResults = new NamedList<Object>();
        for (;;) {
            getXPathResults (rb, xpathResults);
            if (xpathResults.size() >= rb.getQueryCommand().getLen()) {
                break;
            }
            break;
            // get more documents
            // FIXME - not implemented yet
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
        XPath xpath = ((XPathQuery)query).getXPath();
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
            XmlReader reader = new XmlReader ();
            JDOMBuilder jdomBuilder = new JDOMBuilder ();
            reader.addHandler(jdomBuilder);
            try {
                reader.read(new StringReader (xml));
            } catch (XMLStreamException e) {
               throw new IOException (e);
            }
            org.jdom.Document jdom = jdomBuilder.getDocument();
            Object xpathResult;
            try {
                xpathResult = xpath.evaluate(jdom);
            } catch (JaxenException e) {
                throw new IOException (e);
            }
            xpathResults.add("string", xpathResult.toString());
            // TODO: preserve proper type mappings...
            if (xpathResult instanceof Collection) {
                
            }
        }
    }
    
    public static final String COMPONENT_NAME = "xpath";

    @Override
    public String getDescription() {
        return "XPath";
    }

    @Override
    public String getSourceId() {            
        return "lux.XPathSearchHandler$XPathSearchComponent";
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