package lux.functions;

import java.util.ArrayList;

import lux.Evaluator;
import lux.TransformErrorListener;
import lux.query.parser.LuxSearchQueryParser;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.LazySequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

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
    
    public abstract SequenceIterator<? extends Item> iterate(final Item query, final LuxSearchQueryParser parser, final Evaluator eval, final String[] sortCriteria, final int start) throws XPathException;        
    
    public class SearchCall extends NamespaceAwareFunctionCall {
        
        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            
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
            Evaluator eval = getEvaluator(context);
            LuxSearchQueryParser parser = new LuxSearchQueryParser(getNamespaceResolver());
            SequenceIterator<? extends Item> searchIterator = iterate(queryArg, parser, eval, sortCriteria, start);
            return new LazySequence(searchIterator);
        }

    }
  
}
