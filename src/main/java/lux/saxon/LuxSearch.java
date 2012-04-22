package lux.saxon;

import java.io.IOException;

import lux.ResultList;
import lux.ShortCircuitException;
import lux.XPathCollector;
import lux.XPathQuery;
import lux.api.LuxException;
import lux.api.QueryStats;
import lux.api.ValueType;
import lux.xpath.FunCall;
import net.sf.saxon.s9api.ExtensionFunction;
import net.sf.saxon.s9api.ItemType;
import net.sf.saxon.s9api.OccurrenceIndicator;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.SequenceType;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmEmptySequence;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;

import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Version;

/**
 * Executes a Lucene search query and returns documents.
 * 
 * Saxon properties are similar to those for Collection, so just extend that.  We implement
 * both Saxon's internal and external function api's.  It's not clear we need the internal
 * one though?  TODO: eliminate unused code.
 */
public class LuxSearch implements ExtensionFunction {
    
    private XPathQuery query;
    private final Saxon saxon;

    // unused
    /*
    private static final StandardFunction.Entry ENTRY = StandardFunction.makeEntry(
            "lux:search", LuxSearch.class, 0, 0, 2, NodeKindTest.DOCUMENT,
            StaticProperty.ALLOWS_ZERO, StandardFunction.CORE);
    */
    
    protected LuxSearch (XPathQuery query, Saxon saxon) {
        this.query = query;
        this.saxon = saxon;
        //setDetails(ENTRY);
        //setFunctionName(new StructuredQName("lux", FunCall.LUX_NAMESPACE,  "search"));
        //setArguments(new Expression[0]);
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
    /*
    @SuppressWarnings("rawtypes") @Override
    public SequenceIterator<Item> iterate(final XPathContext context) throws XPathException {        
        return new ResultIterator (doSearch (saxon.getContext()));
    }
    
    @SuppressWarnings("rawtypes") @Override
    public SequenceIterator<? extends Item> call(SequenceIterator[] arguments, XPathContext context) throws XPathException {
        if (getNumberOfArguments() == 0 || getNumberOfArguments() > 2) {
            throw new XPathException ("wrong number of arguments");
        }
        Item arg = arguments[0].next();        
        if (arg == null) {
            throw new XPathException ("search arg is null");
        }
        String queryString = arg.getStringValue();
        long facts=0;
        if (getNumberOfArguments() >= 2) {
            IntegerValue num  = (IntegerValue) arguments[1].next();
            facts = num.longValue();
        }
        try {
            query = makeXPathQuery(queryString, facts);
        } catch (ParseException e) {
            throw new XPathException ("Failed to parse lucene query " + queryString, e);
        }
        return iterate (context);
    }
*/
    private XPathQuery makeXPathQuery(String queryString, long facts) throws ParseException {
        Query q;
        q = getQueryParser().parse(queryString);
        return XPathQuery.getQuery(q, facts, ValueType.DOCUMENT);
    }
    
    private QueryParser queryParser;
    protected QueryParser getQueryParser () {
        if (queryParser == null) {
            queryParser = new QueryParser (Version.LUCENE_34, null, new WhitespaceAnalyzer(Version.LUCENE_34));
        }
        return queryParser;
    }
    
    private ResultList<XdmItem> doSearch(SaxonContext context) {
        // TODO: include a context query 
        // Query query = queryContext.getQuery();
        long t = System.nanoTime();
        IndexSearcher searcher = context.getSearcher(); 
        System.out.println ("executing xpath query: " + query);
        XPathCollector collector = saxon.getCollector(query);
        QueryStats stats = saxon.getQueryStats();
        try {
            searcher.search (query.getQuery(), collector);
        } catch (IOException e) {
            throw new LuxException("error searching for query: " + query, e);
        } catch (ShortCircuitException e) {
            // we didn't need to collect all the results
        }
        if (stats != null) {
            stats.totalTime += System.nanoTime() - t;
            stats.docCount += collector.getDocCount();
            stats.query = query.getQuery().toString();
        }
        return (ResultList<XdmItem>) collector.getResults();
    }
    
    public Query getQuery() {
        return query;
    }
    /*
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
    */
    
    public QName getName() {
        return new QName("lux", FunCall.LUX_NAMESPACE, "search");
    }

    public SequenceType getResultType() {
        // new ItemTypeFactory().getNodeKindTest(XdmNodeKind.DOCUMENT);
        return SequenceType.makeSequenceType(ItemType.ANY_NODE, OccurrenceIndicator.ZERO_OR_MORE);
    }

    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] { 
                SequenceType.makeSequenceType(ItemType.STRING, OccurrenceIndicator.ONE),
                SequenceType.makeSequenceType(ItemType.INTEGER, OccurrenceIndicator.ZERO_OR_ONE)
                };
    }

    public XdmValue call(XdmValue[] arguments) throws SaxonApiException {
        String queryString = arguments[0].itemAt(0).getStringValue();
        long facts = 0;
        if (arguments.length > 1) {
            facts = ((XdmAtomicValue)(arguments[1].itemAt(0))).getLongValue();
        }
        try {
            query = makeXPathQuery(queryString, facts);
        } catch (ParseException e) {
            throw new SaxonApiException ("Failed to parse lucene query " + queryString, e);
        }
        ResultList<XdmItem> results = doSearch (saxon.getContext());
        if (results.isEmpty()) {
            return XdmEmptySequence.getInstance();
        }
        if (results.size() == 1) {
            return (XdmValue) results.get(0);
        }
        return new XdmValue (results);
    }
}
