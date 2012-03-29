package lux.saxon;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.functions.Collection;
import net.sf.saxon.functions.StandardFunction;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;

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
    
    private Query query;

    private static final StandardFunction.Entry ENTRY = StandardFunction.makeEntry(
            "lux:search", LuxSearch.class, 0, 0, 0, NodeKindTest.DOCUMENT,
            StaticProperty.ALLOWS_ZERO, StandardFunction.CORE);
    
    public LuxSearch (Query query) {
        this.query = query;
        setDetails(ENTRY);
        setFunctionName(new StructuredQName("lux", "lux", "search"));
        setArguments(new Expression[0]);
    }
    
    /**
     * Iterate over the search results
     *
     * @param context the dynamic context
     * @return an iterator, whose items will always be documents
     * @throws XPathException
     */

    /*@NotNull*/
    @SuppressWarnings("rawtypes")
    public SequenceIterator<Item> iterate(final XPathContext context) throws XPathException {
        // TODO: implement lucene search        
        return EmptyIterator.emptyIterator();
    }

    public Query getQuery() {
        return query;
    }
}
