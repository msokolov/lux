package lux.functions;

import java.util.ArrayList;

import lux.TransformErrorListener;
import lux.search.SearchService;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

/**
 * A base class for functions that parse and execute search queries.
 */
public abstract class SearchBase extends ExtensionFunctionDefinition {

    public SearchBase() {
        super();
    }

    @Override
    public int getMinimumNumberOfArguments() {
        return 1;
    }

    @Override
    public int getMaximumNumberOfArguments() {
        return 1;
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] { SequenceType.SINGLE_ITEM };
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new SearchCall ();
    }
    
    public abstract Sequence iterate(final SearchService searchService, final Item query, final String[] sortCriteria, final int start) throws XPathException;        
    
    public class SearchCall extends NamespaceAwareFunctionCall {
        
        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            long t = System.currentTimeMillis();
            if (arguments.length == 0 || arguments.length > 3) {
                throw new XPathException ("wrong number of arguments for " + getFunctionQName());
            }
            Item queryArg = arguments[0].head();
            String [] sortCriteria = null;
            if (arguments.length >= 2) {
                ArrayList<String> sortCriteriaColl = new ArrayList<String>();
                Sequence sortArg = arguments[1];
                if (sortArg != null) {
                    SequenceIterator<? extends Item> sortArgs = sortArg.iterate();
                    while (sortArgs.next() != null) {
                        sortCriteriaColl.add (sortArgs.current().getStringValue());
                    }
                }
                sortCriteria = sortCriteriaColl.toArray(new String[sortCriteriaColl.size()]);
                // FIXME: use lux_score as default sort criteria, and generate calls in optimizer
                // using document order explicitly
            }
            int start = 1;
            if (arguments.length >= 3) {
                Item startArg = arguments[2].head();
                if (startArg != null) {
                    IntegerValue integerValue = (IntegerValue)startArg;
                    if (integerValue.longValue() > Integer.MAX_VALUE) {
                        throw new XPathException ("integer overflow in search $start parameter");
                    }
                    start = (int) integerValue.longValue();
                }
            }
            SearchService searchService = getSearchService(context);
            searchService.getEvaluator().getQueryStats().totalTime = System.currentTimeMillis() - t;
            return iterate(searchService, queryArg, sortCriteria, start);
        }
        
        /** provide in-scope namespace bindings to the searchService's parser */ 
        protected SearchService getSearchService (XPathContext context) {
            SearchService searchService = SearchBase.getSearchService(context);
            searchService.getParser().setNamespaceResolver (getNamespaceResolver());
            return searchService;
        }

    }

    public static SearchService getSearchService(XPathContext context) {
        TransformErrorListener listener = (TransformErrorListener) context.getController().getErrorListener();
        return (SearchService) listener.getUserData();
    }
  
}
