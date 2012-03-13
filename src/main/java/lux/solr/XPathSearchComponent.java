package lux.solr;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import lux.XPathQuery;
import lux.xml.JDOMBuilder;
import lux.xml.XmlReader;

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
import org.jaxen.JaxenException;
import org.jaxen.XPath;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

public class XPathSearchComponent extends QueryComponent {
    
    private SolrIndexSearcher searcher;
    private IndexSchema schema;
    private Set<String> fields = new HashSet<String>();
    private String xmlFieldName = "xml_text";
    private XMLOutputter xmlOutputter = new XMLOutputter();
    
    public XPathSearchComponent() {
        xmlOutputter.setFormat(Format.getCompactFormat().setOmitDeclaration(true));
    }

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
        while (docs.size() > 0) {
            getXPathResults (rb, xpathResults);
            if (xpathResults.size() >= rb.getQueryCommand().getLen()) {
                break;
            }
            // We need more documents
            int start = rb.req.getParams().getInt(CommonParams.START, 1);
            overrideParamValue(rb, CommonParams.START, start + docs.size());
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
        XPath xpath = ((XPathQuery)query).getXPath();
        DocList docs = (DocList) rb.rsp.getValues().get("response");
        int len = rb.getQueryCommand().getLen();
        DocIterator docIter = docs.iterator();
        if(searcher == null) searcher = rb.req.getSearcher();
        if(schema == null) schema = rb.req.getSchema(); 
        XmlReader reader = new XmlReader ();
        JDOMBuilder jdomBuilder = new JDOMBuilder ();
        reader.addHandler(jdomBuilder);
        while (xpathResults.size() < len) {
            if (docIter.hasNext() == false) {
                break;
            }
            Integer id = docIter.next();
            Document doc = searcher.doc(id, fields);
            String xml = doc.get(xmlFieldName );
            try {
                reader.read(new StringReader (xml));
            } catch (XMLStreamException e) {
               throw new IOException (e);
            }
            org.jdom.Document jdom = jdomBuilder.getDocument();
            getXPathResults(xpathResults, xpath, jdom);
        }
    }

    private void getXPathResults(NamedList<Object> xpathResults, XPath xpath, org.jdom.Document jdom) throws IOException {
        Object xpathResult;
        try {
            xpathResult = xpath.evaluate(jdom);
        } catch (JaxenException e) {
            throw new IOException (e);
        }
        if (xpathResult instanceof Collection) {
            Collection<?> c = (Collection<?>) xpathResult;
            for (Object result :  c) {
                addResult (xpathResults, result);
            }
        } else {
            addResult (xpathResults, xpathResult);
        }
    }
    
    private void addResult(NamedList<Object> xpathResults, Object result) {
        // TODO: review XPath 1.0 types and make sure we're covering them
        if (result instanceof Element) {
            xpathResults.add("element", xmlOutputter.outputString((Element) result));
        } else if (result instanceof org.jdom.Attribute) {
            xpathResults.add ("attribute", ((org.jdom.Attribute)result).getValue());
        } else if (result instanceof org.jdom.Text) {
            xpathResults.add ("text", result.toString());
        } else if (result instanceof org.jdom.Document) {
            xpathResults.add ("document", xmlOutputter.outputString((org.jdom.Document) result));
        } else if (result instanceof Integer) {
            xpathResults.add ("int", result);
        } else if (result instanceof Double) {
            xpathResults.add ("number", result);
        } else {
            xpathResults.add("string", result.toString());
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