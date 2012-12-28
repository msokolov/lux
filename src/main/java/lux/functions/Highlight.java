package lux.functions;

import java.io.IOException;
import java.io.StringReader;

import lux.Evaluator;
import lux.index.analysis.XmlTextTokenStream;
import lux.xml.Offsets;
import lux.xpath.FunCall;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.Serializer.Property;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.value.SequenceType;

import org.apache.lucene.search.Query;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.xmlparser.ParserException;

public class Highlight extends ExtensionFunctionDefinition {

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] { 
                SequenceType.SINGLE_ITEM,
                SequenceType.SINGLE_NODE,
                };
    }
    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "highlight");
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.makeSequenceType(NodeKindTest.DOCUMENT, StaticProperty.EXACTLY_ONE);
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new HighlightCall();
    }
    
    class HighlightCall extends NamespaceAwareFunctionCall {

        @SuppressWarnings("rawtypes")
        @Override
        public SequenceIterator<? extends Item> call(SequenceIterator<? extends Item>[] arguments, XPathContext context)
                throws XPathException {
            Item queryArg = arguments[0].next(); 
            NodeInfo docArg = (NodeInfo) arguments[1].next();
            Query query;
            Evaluator eval = SearchBase.getEvaluator(context);
            try {
                query = parseQuery(queryArg, eval);
            } catch (org.apache.lucene.queryParser.ParseException e) {
                throw new XPathException (e.getMessage(), e);
            } catch (ParserException e) {
                throw new XPathException ("Failed to parse xml query : " + e.getMessage(), e);
            }
            // TODO: optimize!! We are parsing this document, reserializing,
            // highlighting, and parsing again! We could:
            // 1) capture the document text when it's first retrieved from the database, or
            // 2) highlight the document text in situ in the XdmNode?
            Serializer serializer = new Serializer();
            serializer.setOutputProperty(Property.OMIT_XML_DECLARATION, "yes");
            String text;
            XdmNode docNode = new XdmNode(docArg);
            try {
                text = serializer.serializeNodeToString(docNode);
            } catch (SaxonApiException e1) {
                throw new XPathException (e1);
            }
            QueryScorer scorer = new QueryScorer(query);
            Highlighter highlighter = new Highlighter(scorer);
            try {
                String[] fragments = highlighter.getBestFragments(new XmlTextTokenStream(docNode, new Offsets()), text, 1);
                XdmNode highlightDoc = eval.build(new StringReader(fragments[0]), docArg.getSystemId());
                return SingletonIterator.makeIterator(highlightDoc.getUnderlyingNode());
            } catch (IOException e) {
                throw new XPathException(e);
            } catch (InvalidTokenOffsetsException e) {
                throw new XPathException(e);
            }
        }
        
    }

}
