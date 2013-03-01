package lux.functions;

import javax.xml.stream.XMLStreamException;

import lux.Evaluator;
import lux.index.IndexConfiguration;
import lux.search.highlight.HtmlBoldFormatter;
import lux.search.highlight.XmlHighlighter;
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
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.EmptyIterator;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.value.SequenceType;

import org.apache.lucene.search.Query;
import org.apache.lucene.xmlparser.ParserException;

/**
 * <code>lux:highlight($query as item(), $node as node())</code> <p>returns
 * the given node with text matching the query surrounded by HTML B tags.
 * The query may be a string or an element/document of the same types
 * supported by lux:search.</p> <p>TODO: enable control over the highlight
 * tagging. </p>
 * @see Search
 */
public class Highlight extends ExtensionFunctionDefinition {

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] { 
                SequenceType.SINGLE_ITEM,
                SequenceType.OPTIONAL_NODE,
                };
    }
    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "highlight");
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.makeSequenceType(NodeKindTest.DOCUMENT, StaticProperty.ALLOWS_ZERO_OR_ONE);
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
            if (docArg == null) {
                return EmptyIterator.emptyIterator();
            }
            Query query;
            Evaluator eval = SearchBase.getEvaluator(context);
            try {
                query = parseQuery(queryArg, eval);
            } catch (org.apache.lucene.queryParser.ParseException e) {
                throw new XPathException (e.getMessage(), e);
            } catch (ParserException e) {
                throw new XPathException ("Failed to parse xml query : " + e.getMessage(), e);
            }
            IndexConfiguration indexConfiguration = eval.getCompiler().getIndexConfiguration();
            XmlHighlighter xmlHighlighter = new XmlHighlighter(eval.getCompiler().getProcessor(), indexConfiguration, new HtmlBoldFormatter());
            try {
                XdmNode highlighted = xmlHighlighter.highlight(query, docArg);
                return SingletonIterator.makeIterator(highlighted.getUnderlyingNode());
            } catch (XMLStreamException e) {
                throw new XPathException(e);
            } catch (SaxonApiException e) {
                throw new XPathException(e);
            }
        }
        
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
