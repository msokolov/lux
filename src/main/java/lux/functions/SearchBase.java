package lux.functions;

import org.apache.lucene.search.Query;
import org.apache.solr.handler.component.ResponseBuilder;

import lux.Evaluator;
import lux.QueryContext;
import lux.TransformErrorListener;
import lux.solr.SolrQueryContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

/**
 * A base class for functions that execute search queries.
 */
public abstract class SearchBase extends ExtensionFunctionDefinition {
    
    public enum QueryParser {
        CLASSIC, XML
    }

    public SearchBase() {
        super();
    }

    @SuppressWarnings("rawtypes")
    protected abstract SequenceIterator<? extends Item> iterate(final Query query, final Evaluator eval, final String sortCriteria, final int start) throws XPathException;

    @SuppressWarnings("rawtypes")
    protected abstract SequenceIterator<? extends Item> iterateDistributed(final String query, final QueryParser queryParser, final Evaluator eval, final String sortCriteria, final int start) throws XPathException;

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
    
    public static Evaluator getEvaluator (XPathContext context) {
        // TODO: check thread safety of controller's error listener
        TransformErrorListener listener = (TransformErrorListener) context.getController().getErrorListener();
        return (Evaluator) listener.getUserData();
    }
    
    public class SearchCall extends NamespaceAwareFunctionCall {
        
        @SuppressWarnings("rawtypes") @Override
        public SequenceIterator<? extends Item> call(SequenceIterator[] arguments, XPathContext context) throws XPathException {
            
            if (arguments.length == 0 || arguments.length > 3) {
                throw new XPathException ("wrong number of arguments for " + getFunctionQName());
            }
            Item queryArg = arguments[0].next();
            String sortCriteria = null;
            if (arguments.length >= 2) {
                Item sortArg = arguments[1].next();
                if (sortArg != null) {
                    sortCriteria = sortArg.getStringValue();
                }
            }
            int start = 1;
            if (arguments.length >= 3) {
                Item startArg = arguments[2].next();
                if (startArg != null) {
                    IntegerValue integerValue = (IntegerValue)startArg;
                    if (integerValue.longValue() > Integer.MAX_VALUE) {
                        throw new XPathException ("integer overflow in search $start parameter");
                    }
                    start = (int) integerValue.longValue();
                }
            }
            Evaluator eval = getEvaluator(context);
            QueryContext queryContext = eval.getQueryContext();
            if (queryContext instanceof SolrQueryContext) {
                ResponseBuilder rb = ((SolrQueryContext) queryContext).getResponseBuilder() ;
                if (rb != null && rb.shards != null) {
                    // For cloud queries, we don't parse; just serialize the query and let the shard parse it
                    QueryParser qp;
                    String qstr;
                    if (queryArg instanceof NodeInfo) {
                        qp = QueryParser.XML;
                        // cheap-ass serialization
                        qstr = new XdmNode((NodeInfo)queryArg).toString();
                    } else {
                        qp = QueryParser.CLASSIC;
                        qstr = queryArg.getStringValue();
                    }
                    return iterateDistributed (qstr, qp, eval, sortCriteria, start);
                }
            }
            return iterate (parseQuery(queryArg, eval), eval, sortCriteria, start);
        }
        
    }
  
}
