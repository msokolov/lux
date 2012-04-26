package lux.saxon;

import java.io.IOException;
import java.io.StringReader;

import lux.SingleFieldSelector;
import lux.XPathQuery;
import lux.api.QueryStats;
import lux.xpath.FunCall;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.util.Version;

/**
 * Executes a Lucene search query and returns documents.
 * 
 */
public class LuxSearch extends ExtensionFunctionDefinition {
    protected final Saxon saxon;
    
    public LuxSearch (Saxon saxon) {
        this.saxon = saxon;
    }
    
    private XPathQuery makeXPathQuery(String queryString, long facts) throws ParseException {
        Query q;
        q = getQueryParser().parse(queryString);
        return XPathQuery.getQuery(q, facts);
    }
    
    private QueryParser queryParser;
    protected QueryParser getQueryParser () {
        if (queryParser == null) {
            queryParser = new QueryParser (Version.LUCENE_34, null, new WhitespaceAnalyzer(Version.LUCENE_34));
        }
        return queryParser;
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] { 
                SequenceType.SINGLE_STRING,
                SequenceType.OPTIONAL_INTEGER
                };
    }
    
    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "search");
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.makeSequenceType(NodeKindTest.DOCUMENT, StaticProperty.ALLOWS_ZERO_OR_MORE);
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new LuxSearchCall ();
    }
    

    /**
     * Iterate over the search results
     *
     * @param query the query to execute
     * @return an iterator with the results of executing the query and applying the
     * expression to its result.
     * @throws XPathException
     */        
    @SuppressWarnings("rawtypes")
    public SequenceIterator<Item> iterate(final XPathQuery query) throws XPathException {        
        try {
            return new ResultIterator (query);
        } catch (IOException e) {
            throw new XPathException (e);
        }
    }
    
    class LuxSearchCall extends ExtensionFunctionCall {
        
        private XPathQuery query;
        
        @SuppressWarnings("rawtypes") @Override
        public SequenceIterator<? extends Item> call(SequenceIterator[] arguments, XPathContext context) throws XPathException {
            if (arguments.length == 0 || arguments.length > 2) {
                throw new XPathException ("wrong number of arguments");
            }
            Item arg = arguments[0].next();        
            if (arg == null) {
                throw new XPathException ("search arg is null");
            }
            String queryString = arg.getStringValue();
            long facts=0;
            if (arguments.length >= 2) {
                IntegerValue num  = (IntegerValue) arguments[1].next();
                facts = num.longValue();
            }
            try {
                query = makeXPathQuery(queryString, facts);
            } catch (ParseException e) {
                throw new XPathException ("Failed to parse lucene query " + queryString, e);
            }
            System.out.println ("executing xpath query: " + query);
            return iterate (query);
        }
        
    }
    
    @SuppressWarnings("rawtypes")
    class ResultIterator implements SequenceIterator<Item>{
        
        private final DocIdSetIterator docIter;
        private final XPathQuery query;
        private Item current = null;
        private int position = 0;
        private final QueryStats stats;
        
        ResultIterator (final XPathQuery query) throws IOException {
            this.query = query;
            docIter = saxon.getContext().getSearcher().search(query);
            stats = saxon.getQueryStats();
            if (stats != null) {
                stats.query = query.getQuery().toString();
            }
        }

        public Item next() throws XPathException {
            long t = System.nanoTime();
            try {
                int docID = docIter.nextDoc();
                if (docID == Scorer.NO_MORE_DOCS) {
                    position = -1;
                    current = null;
                } else {
                    String xmlFieldName = saxon.getContext().getXmlFieldName();
                    Document document;
                    document = saxon.getContext().getSearcher().getIndexReader().
                            document(docID, new SingleFieldSelector(xmlFieldName));

                    String xml = document.get(xmlFieldName);
                    XdmItem xdmItem;
                    xdmItem = (XdmItem) saxon.getBuilder().build(new StringReader (xml));
                    current = (Item) xdmItem.getUnderlyingValue();
                    ++position;
                    stats.retrievalTime += System.nanoTime() - t;
                }
            } catch (IOException e) {
                throw new XPathException(e);
            } finally {
                if (stats != null) {
                    if (position >= 0) {
                        stats.docCount = position;
                    }
                    stats.totalTime += System.nanoTime() - t;
                }
            }
            return current;
        }

        public Item current() {
            return current;
        }

        public int position() {
            return position;
        }

        public void close() {
        }

        public SequenceIterator<Item> getAnother() throws XPathException {
            return iterate (query);
        }

        public int getProperties() {
            return SequenceIterator.LOOKAHEAD;
        }
        
    }
}
