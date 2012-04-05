package lux.saxon;

import java.util.Iterator;

import lux.ResultList;
import lux.XPathQuery;
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
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.LookaheadIterator;

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
public class LuxSearch extends Collection {
    
    private final XPathQuery query;
    private final Saxon saxon;

    private static final StandardFunction.Entry ENTRY = StandardFunction.makeEntry(
            "lux:search", LuxSearch.class, 0, 0, 0, NodeKindTest.DOCUMENT,
            StaticProperty.ALLOWS_ZERO, StandardFunction.CORE);
    
    public LuxSearch (XPathQuery query, Saxon saxon) {
        this.query = query;
        this.saxon = saxon;
        setDetails(ENTRY);
        setFunctionName(new StructuredQName("lux", "lux", "search"));
        setArguments(new Expression[0]);
    }
    
    /**
     * Iterate over the search results
     *
     * @param context the dynamic context
     * @return an iterator with the results of executing the query and applying the
     * expression to its result.
     * @throws XPathException
     */

    /*@NotNull*/
    @SuppressWarnings("rawtypes")
    public SequenceIterator<Item> iterate(final XPathContext context) throws XPathException {      
        return new ResultIterator (saxon.evaluate(query.getExpression()));
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
}
