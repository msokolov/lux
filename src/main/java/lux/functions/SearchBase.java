package lux.functions;

import lux.Evaluator;
import lux.TransformErrorListener;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceExtent;
import net.sf.saxon.value.SequenceType;

import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.xml.ParserException;
import org.apache.lucene.search.Query;

import org.slf4j.LoggerFactory;

/**
 * A base class for functions that execute search queries.
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
        return 2;
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] { 
                SequenceType.SINGLE_ITEM,
                SequenceType.OPTIONAL_INTEGER
                };
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new SearchCall ();
    }
    
    public static Evaluator getEvaluator (XPathContext context) {
        TransformErrorListener listener = (TransformErrorListener) context.getController().getErrorListener();
        return (Evaluator) listener.getUserData();
    }
    
    @SuppressWarnings("rawtypes")
    protected abstract SequenceIterator<? extends Item> iterate(final Query query, Evaluator eval, long facts, String sortCriteria, int start) throws XPathException;

    public class SearchCall extends NamespaceAwareFunctionCall {
        
        @SuppressWarnings("rawtypes") @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            
            if (arguments.length == 0 || arguments.length > 4) {
                throw new XPathException ("wrong number of arguments for " + getFunctionQName());
            }
            Item queryArg = arguments[0].head();
            long facts=0;
            if (arguments.length >= 2) {
                IntegerValue num  = (IntegerValue) arguments[1].head();
                if (num != null) {
                    facts = num.longValue();
                }
            }
            String sortCriteria = null;
            if (arguments.length >= 3) {
                Item sortArg = arguments[2].head();
                if (sortArg != null) {
                    sortCriteria = sortArg.getStringValue();
                }
            }
            int start = 1;
            if (arguments.length >= 4) {
                Item startArg = arguments[3].head();
                if (startArg != null) {
                    IntegerValue integerValue = (IntegerValue)startArg;
                    if (integerValue.longValue() > Integer.MAX_VALUE) {
                        throw new XPathException ("integer overflow in search $start parameter");
                    }
                    start = (int) integerValue.longValue();
                }
            }
            Evaluator eval = getEvaluator(context);
            Query query;
            try {
                query = parseQuery(queryArg, eval);
            } catch (ParseException e) {
                throw new XPathException (e.getMessage(), e);
            } catch (ParserException e) {
                throw new XPathException ("Failed to parse xml query : " + e.getMessage(), e);
            }
            LoggerFactory.getLogger(SearchBase.class).debug("executing query: {}", query);
            return new SequenceExtent(iterate (query, eval, facts, sortCriteria, start));
        }

    }
    
    
  
}