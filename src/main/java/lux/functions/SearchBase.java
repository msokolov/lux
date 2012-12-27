package lux.functions;

import java.util.Iterator;

import lux.Evaluator;
import lux.Evaluator.LuxCollectionURIResolver;
import lux.query.parser.LuxQueryParser;
import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

import org.apache.lucene.search.Query;
import org.apache.lucene.xmlparser.ParserException;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

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
    
    @SuppressWarnings("rawtypes")
    protected abstract SequenceIterator<? extends Item> iterate(final Query query, Evaluator eval, long facts, String sortCriteria) throws XPathException;

    class SearchCall extends NamespaceAwareFunctionCall {
        
        @SuppressWarnings("rawtypes") @Override
        public SequenceIterator<? extends Item> call(SequenceIterator[] arguments, XPathContext context) throws XPathException {
            
            if (arguments.length == 0 || arguments.length > 3) {
                throw new XPathException ("wrong number of arguments");
            }
            Item queryArg = arguments[0].next();        
            long facts=0;
            if (arguments.length >= 2) {
                IntegerValue num  = (IntegerValue) arguments[1].next();
                if (num != null) {
                    facts = num.longValue();
                }
            }
            String sortCriteria = null;
            if (arguments.length >= 3) {
                Item sortArg = arguments[2].next();
                if (sortArg != null) {
                    sortCriteria = sortArg.getStringValue();
                }
            }
            LuxCollectionURIResolver resolver = (Evaluator.LuxCollectionURIResolver) context.getConfiguration().getCollectionURIResolver();
            Evaluator eval = resolver.getEvaluator();
            Query query;
            try {
                query = parseQuery(queryArg, eval);
            } catch (org.apache.lucene.queryParser.ParseException e) {
                throw new XPathException (e.getMessage(), e);
            } catch (ParserException e) {
                throw new XPathException ("Failed to parse xml query : " + e.getMessage(), e);
            }
            LoggerFactory.getLogger(SearchBase.class).debug("executing query: {}", query);
            return iterate (query, eval, facts, sortCriteria);
        }
        
       private Query parseQuery(Item<?> queryArg, Evaluator eval) throws org.apache.lucene.queryParser.ParseException, ParserException {
            if (queryArg instanceof NodeInfo) {
                NodeInfo queryNodeInfo = (NodeInfo) queryArg;
                NodeOverNodeInfo queryDocument = NodeOverNodeInfo.wrap(queryNodeInfo); 
                if (queryDocument instanceof Element) {
                    return eval.getXmlQueryParser().getQuery((Element) queryDocument);
                }
                // maybe it was a text node?
            }
            // parse the string value using the Lux query parser
            LuxQueryParser luxQueryParser = eval.getLuxQueryParser();
            // declare all of the in-scope namespace bindings
            NamespaceResolver namespaceResolver = getNamespaceResolver();
            Iterator<String> prefixes = namespaceResolver.iteratePrefixes();
            while (prefixes.hasNext()) {
                String prefix = prefixes.next();
                String nsURI = namespaceResolver.getURIForPrefix(prefix, false);
                if (! NamespaceConstant.isReservedInQuery(nsURI)) {
                    luxQueryParser.bindNamespacePrefix(prefix, nsURI);
                }
            }
            return luxQueryParser.parse(queryArg.getStringValue());
        }
        
    }
  
}