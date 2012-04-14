package lux.saxon;

import java.io.IOException;
import java.util.Iterator;

import lux.ResultList;
import lux.ShortCircuitException;
import lux.XPathCollector;
import lux.XPathQuery;
import lux.api.LuxException;
import lux.api.QueryStats;
import lux.xpath.FunCall;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.LastPositionFinder;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.Collection;
import net.sf.saxon.functions.StandardFunction;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.s9api.ExtensionFunction;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.s9api.OccurrenceIndicator;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.SequenceType;
import net.sf.saxon.s9api.XdmEmptySequence;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.LookaheadIterator;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

/**
 * Executes a Lucene search query and returns documents.
 * 
 * Saxon properties are similar to those for Collection, so just extend that.
 * 
 * The Query is provided in the constructor, so this method is for internal use only.
 * TODO: provide an argument allowing a query to passed in as a string and parsed by Lucene's
 * query parser?
 *
 */
public class LuxSearch extends Collection implements ExtensionFunction {
    
    private XPathQuery query;
    private final Saxon saxon;
    private QueryStats queryStats;

    private static final StandardFunction.Entry ENTRY = StandardFunction.makeEntry(
            "lux:search", LuxSearch.class, 0, 0, 0, NodeKindTest.DOCUMENT,
            StaticProperty.ALLOWS_ZERO, StandardFunction.CORE);
    
    public LuxSearch (XPathQuery query, Saxon saxon) {
        this.query = query;
        this.saxon = saxon;
        queryStats = new QueryStats();
        setDetails(ENTRY);
        setFunctionName(new StructuredQName("lux", FunCall.LUX_NAMESPACE,  "search"));
        setArguments(new Expression[0]);
    }
    
    public LuxSearch (Saxon saxon) {
        this (null, saxon);
    }
    
    /**
     * Iterate over the search results
     *
     * @param context the dynamic context; the context is ignored by search().
     * @return an iterator with the results of executing the query and applying the
     * expression to its result.
     * @throws XPathException
     */
    @SuppressWarnings("rawtypes") @Override
    public SequenceIterator<Item> iterate(final XPathContext context) throws XPathException {        
        return new ResultIterator (doSearch (saxon.getContext()));
    }
    
    @SuppressWarnings("rawtypes") @Override
    public SequenceIterator<? extends Item> call(SequenceIterator[] arguments, XPathContext context) throws XPathException {
        if (getNumberOfArguments() != 1) {
            throw new XPathException ("wrong number of arguments");
        }
        Item arg = arguments[0].next();        
        if (arg == null) {
            throw new XPathException ("search arg is null");
        }
        query = parseQuery (arg.getStringValue());
        return iterate (context);
    }

    private XPathQuery parseQuery(String stringValue) {
        // TODO - parse lucene query
        return null;
    }
    
    private ResultList<XdmItem> doSearch(SaxonContext context) {
        // TODO: include a context query 
        // Query query = queryContext.getQuery();
        long t = System.nanoTime();
        IndexSearcher searcher = context.getSearcher(); 
        System.out.println ("executing xpath query: " + query);
        XPathCollector collector = new XPathCollector (query, saxon.getBuilder(), queryStats);
        try {
            searcher.search (query.getQuery(), collector);
        } catch (IOException e) {
            throw new LuxException("error searching for query: " + query, e);
        } catch (ShortCircuitException e) {
            // we didn't need to collect all the results
        }
        queryStats.totalTime = System.nanoTime() - t;
        queryStats.docCount = collector.getDocCount();
        queryStats.query = query.getQuery().toString();
        return (ResultList<XdmItem>) collector.getResults();
    }

    
    public Query getQuery() {
        return query;
    }
    
    @SuppressWarnings("rawtypes")
    class ResultIterator implements LookaheadIterator<Item>, LastPositionFinder<Item> {
        
        private final Iterator<?> resultIter;
        private final ResultList<?> results;
        private Item current = null;
        private int position = 0;
        
        ResultIterator (ResultList<?> results) {
            this.results = results;
            resultIter = results.iterator();
        }

        public Item next() throws XPathException {
            if (! resultIter.hasNext()) {
                position = -1;
                current = null;
            } else {
                XdmItem xdmItem = (XdmItem) resultIter.next();
                current = (Item) xdmItem.getUnderlyingValue();
                ++position;
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
            return new ResultIterator (results);
        }

        public int getProperties() {
            return SequenceIterator.LOOKAHEAD;
        }

        public boolean hasNext() {
            return resultIter.hasNext();
        }

        public int getLength() throws XPathException {
            return results.size();
        }
        
    }

    public QName getName() {
        return new QName("lux", FunCall.LUX_NAMESPACE, "search");
    }

    public SequenceType getResultType() {
        // new ItemTypeFactory().getNodeKindTest(XdmNodeKind.DOCUMENT);
        return SequenceType.makeSequenceType(ItemType.ANY_NODE, OccurrenceIndicator.ZERO_OR_MORE);
    }

    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] { SequenceType.makeSequenceType(ItemType.STRING, OccurrenceIndicator.ONE) };
    }

    public XdmValue call(XdmValue[] arguments) throws SaxonApiException {
        query = parseQuery (arguments[0].itemAt(0).getStringValue());
        ResultList<XdmItem> results =  doSearch (saxon.getContext());
        if (results.isEmpty()) {
            return XdmEmptySequence.getInstance();
        }
        if (results.size() == 1) {
            return (XdmValue) results.get(0);
        }
        return new XdmValue (results);
    }
}
