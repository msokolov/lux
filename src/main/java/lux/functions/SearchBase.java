package lux.functions;

import lux.Evaluator;
import lux.index.FieldName;
import lux.index.IndexConfiguration;
import lux.index.field.FieldDefinition;
import lux.query.parser.LuxQueryParser;
import lux.query.parser.XmlQueryParser;
import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.IntegerValue;
import net.sf.saxon.value.SequenceType;

import org.apache.lucene.search.Query;
import org.apache.lucene.xmlparser.CoreParser;
import org.apache.lucene.xmlparser.ParserException;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

public abstract class SearchBase extends ExtensionFunctionDefinition {

    private LuxQueryParser luxQueryParser;
    private CoreParser xmlQueryParser;

    public SearchBase() {
        super();
    }

    protected Query parseQuery(Item<?> queryArg, IndexConfiguration config) throws org.apache.lucene.queryParser.ParseException, ParserException {
        if (queryArg instanceof NodeInfo) {
            NodeInfo queryNodeInfo = (NodeInfo) queryArg;
            NodeOverNodeInfo queryDocument = NodeOverNodeInfo.wrap(queryNodeInfo); 
            if (queryDocument instanceof Element) {
                return getXmlQueryParser(config).getQuery((Element) queryDocument);
            }
            // maybe it was a text node?
        }
        // parse the string value using the Lux query parser
        return getLuxQueryParser(config).parse(queryArg.getStringValue());
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

    protected LuxQueryParser getLuxQueryParser(IndexConfiguration xmlIndexer) {
        if (luxQueryParser == null) {
            luxQueryParser = new LuxQueryParser (xmlIndexer);
        }
        return luxQueryParser;
    }

    protected CoreParser getXmlQueryParser(IndexConfiguration xmlIndexer) {
        if (xmlQueryParser == null) {
            FieldDefinition field = xmlIndexer.getField(FieldName.ELEMENT_TEXT);
            xmlQueryParser = new XmlQueryParser(xmlIndexer.getFieldName(field), field.getAnalyzer());
        }
        return xmlQueryParser;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new LuxSearchCall ();
    }
    
    @SuppressWarnings("rawtypes")
    protected abstract SequenceIterator<? extends Item> iterate(final Query query, Evaluator eval, long facts) throws XPathException;

    class LuxSearchCall extends ExtensionFunctionCall {
        
        @SuppressWarnings("rawtypes") @Override
        public SequenceIterator<? extends Item> call(SequenceIterator[] arguments, XPathContext context) throws XPathException {
            
            if (arguments.length == 0 || arguments.length > 2) {
                throw new XPathException ("wrong number of arguments");
            }
            Item queryArg = arguments[0].next();        
            long facts=0;
            if (arguments.length >= 2) {
                IntegerValue num  = (IntegerValue) arguments[1].next();
                facts = num.longValue();
            }
            Evaluator eval = (Evaluator) context.getConfiguration().getCollectionURIResolver();
            Query query;
            try {
                query = parseQuery(queryArg, eval.getCompiler().getIndexConfiguration());
            } catch (org.apache.lucene.queryParser.ParseException e) {
                throw new XPathException ("Failed to parse lucene query " + queryArg.getStringValue(), e);
            } catch (ParserException e) {
                throw new XPathException ("Failed to parse xml query " + queryArg.toString(), e);
            }
            LoggerFactory.getLogger(SearchBase.class).debug("executing query: {}", query);
            return iterate (query, eval, facts);
        }
        
    }
  
}