package lux.functions;

import java.util.Iterator;

import lux.Evaluator;
import lux.query.parser.LuxQueryParser;
import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.instruct.SavedNamespaceContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.NodeInfo;

import org.apache.lucene.search.Query;
import org.apache.lucene.xmlparser.ParserException;
import org.w3c.dom.Element;

public abstract class NamespaceAwareFunctionCall extends ExtensionFunctionCall {

    private NamespaceResolver namespaceResolver;
    
    public NamespaceResolver getNamespaceResolver() {
        return namespaceResolver;
    }

    @Override
    public void supplyStaticContext (StaticContext context, int locationId, Expression[] arguments) {
        namespaceResolver = context.getNamespaceResolver();
        if (!(namespaceResolver instanceof SavedNamespaceContext)) {
            namespaceResolver = new SavedNamespaceContext(namespaceResolver);
        }
    }
    
    @Override
    public void copyLocalData (ExtensionFunctionCall destination) {
        ((NamespaceAwareFunctionCall) destination).namespaceResolver = namespaceResolver;
    }
    
   protected Query parseQuery(Item<?> queryArg, Evaluator eval) throws org.apache.lucene.queryParser.ParseException, ParserException {
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
