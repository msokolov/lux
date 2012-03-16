package lux;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import lux.xml.JDOMBuilder;
import lux.xml.XmlReader;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.FieldSelectorResult;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Scorer;
import org.jaxen.BaseXPath;
import org.jaxen.Context;
import org.jaxen.JaxenException;
import org.jaxen.Navigator;
import org.jaxen.expr.Expr;
import org.jaxen.jdom.DocumentNavigator;

/**
 * Executes XPath queries against a Lux (Lucene XML) datastore.
 */
public abstract class LuXPath extends BaseXPath
{
    
    private QueryStats queryStats;
    
    /**
     * Creates an XPathSolr backed by the default org.w3c.DOM-based XPath
     * Navigator implementation.
     * @param xpathExpr the expression to evaluate
     */
    public LuXPath(String xpathExpr) throws JaxenException {
        super (xpathExpr, DocumentNavigator.getInstance());
    }

    /**
     * Creates an XPathSolr 
     * @param xpathExpr the expression to evaluate
     * @param navigator the supporting XPath Navigator implementation. This
     * is used to evaluate any expressions that are not resolvable directly
     * (ie out of the index).
     */
    public LuXPath(String xpathExpr, Navigator navigator) throws JaxenException {
        super (xpathExpr, navigator);
    }

    /** If the context is a Collection, then execute the xpath expression
     * against all documents in the Collection.  Otherwise, evaluate the
     * XPath normally.
     *
     * @param context the Context which gets evaluated
     *
     * @return the node-set of all items selected by this XPath expression
     * @throws JaxenException if an XPath error occurs during expression evaluation
     *
     */
    protected List<?> selectNodesForContext(Context context) throws JaxenException
    {
        if (context instanceof QueryContext) {
            long t = System.nanoTime();
            ArrayList<Object> results = new ArrayList<Object>();
        	QueryContext queryContext = (QueryContext) context;
        	IndexSearcher searcher = queryContext.getSearcher();        	
            XPathQuery xpq = getQuery (getRootExpr(), context);
            try {
                queryStats = new QueryStats();
                XPathCollector collector = new XPathCollector(queryContext, results);
                searcher.search (xpq.getQuery(), collector);
                queryStats.totalTime = System.nanoTime() - t;
                queryStats.docCount = collector.getDocCount();
                queryStats.query = xpq.getQuery().toString();
            } catch (IOException e) {
               if (e.getCause() instanceof JaxenException)
                   throw ((JaxenException) e.getCause());
               throw new JaxenException (e);
            }
        	return results;
        } else {
            return super.selectNodesForContext (context);
        }
    }

    private org.jdom.Document getXmlDocument(XmlReader xmlReader) {
        return ((JDOMBuilder)xmlReader.getHandlers().get(0)).getDocument();
    }
    
    final static class SingleFieldSelector implements FieldSelector {
        
        private final String fieldName;
        
        SingleFieldSelector (String fieldName) {
            this.fieldName = fieldName;
        }

        public FieldSelectorResult accept(String fieldName) {
            return this.fieldName.equals(fieldName) ? FieldSelectorResult.LOAD : FieldSelectorResult.NO_LOAD;
        }
        
    }
    
    public boolean dontParse = false; // for testing oNLY!
    
    class XPathCollector extends Collector {

        private IndexReader reader;
        private final QueryContext queryContext;
        private final List<Object> results;
        private int docCount = 0;
        
        XPathCollector (QueryContext context, List<Object> results) {
            this.queryContext = context;
            this.results = results;
        }
        
        @Override
        public void setScorer(Scorer scorer) throws IOException {
        }

        @SuppressWarnings("unchecked")
        @Override
        public void collect(int doc) throws IOException {
            long t = System.nanoTime();
            ++ docCount;

            //if (xpq.isMinimal()) {
                // TODO return the query results as a list of values
                // of the appropriate type and maybe don't even retrieve documents?
            //}
            
            Document document = reader.document(doc, new SingleFieldSelector(queryContext.getXmlFieldName()));
            String xml = document.get(queryContext.getXmlFieldName());
            if (dontParse) {
                results.add (xml);
                return; // simulate effect of parse-free xpath eval returning an entire document
            }
            XmlReader xmlReader = getXmlReader();
            try {
                xmlReader.read(new StringReader (xml));
            } catch (XMLStreamException e) {
                throw new IOException (e);
            }
            try {
                results.addAll(selectNodes(getXmlDocument(xmlReader)));
            } catch (JaxenException e) {
                throw new IOException (e);
            }
            t = System.nanoTime() - t;
            queryStats.collectionTime += t;
        }

        @Override
        public void setNextReader(IndexReader reader, int docBase) throws IOException {
            this.reader = reader;
        }

        @Override
        public boolean acceptsDocsOutOfOrder() {
            return true;
        }
        
        public int getDocCount () {
            return docCount;
        }
        
    }
    
    /**
     * A class that holds statistics about the last query execution
     */
    public class QueryStats {
        /**
         * the number of documents that matched the lucene query. If XPath was executed (there wasn't
         * a short-circuited eval of some sort), this number of XML documents will have been retrieved
         * from the database and processed.
         */
        public int docCount;
        
        /**
         * time spent collecting results (parsing and computing xpath, mostly), in nanoseconds
         */
        public long collectionTime;
        
        /*
         * total time to evaluate the query and produce results, in nanoseconds
         */
        public long totalTime;
        
        /**
         * A description of the work done prior to collection; usu. the Lucene Query generated from the XPath and used to retrieve a set of candidate
         * documents for evaluation.
         */
        public String query;
    }
    
    public QueryStats getQueryStats() {
        return queryStats;
    }
    
    private XmlReader getXmlReader () {
        XmlReader reader = new XmlReader();
        JDOMBuilder builder = new JDOMBuilder();
        reader.addHandler(builder);
        return reader;
    }
    
    protected abstract XPathQuery getQuery (Expr expr, Context context) throws JaxenException;

}
