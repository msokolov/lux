package lux.query.parser;

import java.util.ArrayList;
import java.util.Iterator;

import lux.Evaluator;
import net.sf.saxon.dom.DocumentOverNodeInfo;
import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.expr.instruct.SavedNamespaceContext;
import net.sf.saxon.lib.NamespaceConstant;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NamespaceResolver;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.trans.XPathException;

import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.xml.ParserException;
import org.apache.lucene.search.Query;
import org.w3c.dom.Element;

/** 
 * Provides parsing service to lux:search() and related function calls.
 */
public class LuxSearchQueryParser {
    
    private NamespaceResolver namespaceResolver;
    
    /**
     * Constructs a parser with default namespace bindings
     */
    public LuxSearchQueryParser () {
        // TODO: define default namespace bindings
        this.namespaceResolver = new SavedNamespaceContext(new ArrayList<NamespaceBinding>());
    }

    /**
     * Parses a query represented as an Item  
     * @param query the query, as an Item - if it is a node, we expect an element and handle it 
     * with the XmlQueryParser.  Otherwise, the query's string value is parsed using the NodeQueryParser.
     * @param eval the query Evaluator
     * @return a Lucene Query
     * @throws XPathException
     */
    public Query parse(Item queryArg, Evaluator eval) throws XPathException {
        if (queryArg instanceof NodeInfo) {
            NodeInfo queryNodeInfo = (NodeInfo) queryArg;
            return parse(queryNodeInfo, eval);
        }
        return parse(queryArg.getStringValue(), eval);
    }

    /**
     * Parses a query represented as XML; if the node is a text node, the query is parsed as a string
     * @param query the query, encoded as an XML element using the XmlQueryParser
     * @param eval the query Evaluator
     * @return a Lucene Query
     * @throws XPathException if there is a parsing error
     */
    public Query parse(NodeInfo queryNodeInfo, Evaluator eval) throws XPathException {
        NodeOverNodeInfo queryNode = NodeOverNodeInfo.wrap(queryNodeInfo); 
        if (queryNode instanceof Element || queryNode instanceof DocumentOverNodeInfo) {
            try {
                return eval.getXmlQueryParser().getQuery((Element) queryNode);
            } catch (ParserException e) {
                throw new XPathException (e.getMessage(), e);
            }
        }
        return parse (queryNodeInfo.getStringValue(), eval);
    }
    
    public Query parse(String query, Evaluator eval) throws XPathException {
        // parse the string value using the Lux query parser
        NodeQueryParser luxQueryParser = eval.getLuxQueryParser();
        // declare all of the in-scope namespace bindings
        Iterator<String> prefixes = namespaceResolver.iteratePrefixes();
        while (prefixes.hasNext()) {
            String prefix = prefixes.next();
            String nsURI = namespaceResolver.getURIForPrefix(prefix, false);
            if (! NamespaceConstant.isReservedInQuery(nsURI)) {
                luxQueryParser.bindNamespacePrefix(prefix, nsURI);
            }
        }
        try {
            return luxQueryParser.parse(query);
        } catch (ParseException e) {
            throw new XPathException (e.getMessage(), e);
        }
    }

    public void setNamespaceResolver(NamespaceResolver resolver) {
        this.namespaceResolver = resolver;
    }

}
