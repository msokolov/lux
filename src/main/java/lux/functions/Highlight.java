package lux.functions;

import javax.xml.stream.XMLStreamException;

import lux.Evaluator;
import lux.index.IndexConfiguration;
import lux.search.highlight.TagFormatter;
import lux.search.highlight.XmlHighlighter;
import lux.xpath.FunCall;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.*;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;
import net.sf.saxon.value.StringValue;
import net.sf.saxon.value.QNameValue;

import org.apache.lucene.search.Query;

/**
 * <code>lux:highlight($node as node()?, $query as item(), $tag as item())</code><br/>
 * <code>lux:highlight($node as node()?, $query as item())</code>
 * <p>returns
 * the given node with text matching the query surrounded by the given $tag (or B if no tag is given).
 * The query may be a string or an element/document of the same types supported by lux:search.</p>
 * <p>The tag may be specified as either a QName or a string; if a string, an element 
 * is created with no namespace.</p>
 * @see Search
 */
public class Highlight extends ExtensionFunctionDefinition {

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] { 
                SequenceType.OPTIONAL_NODE,
                SequenceType.SINGLE_ITEM,
                SequenceType.SINGLE_ITEM,
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
    
    @Override
    public int getMinimumNumberOfArguments () {
    	return 2;
    }
    
    @Override
    public int getMaximumNumberOfArguments () {
    	return 3;
    }
    
    class HighlightCall extends NamespaceAwareFunctionCall {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments)
                throws XPathException {
            NodeInfo docArg = (NodeInfo) arguments[0].head();
            if (docArg == null) {
                return EmptySequence.getInstance();
            }
            Item queryArg = arguments[1].head(); 
            Evaluator eval = SearchBase.getEvaluator(context);
            Query query = parseQuery(queryArg, eval);
            IndexConfiguration indexConfiguration = eval.getCompiler().getIndexConfiguration();
            TagFormatter formatter;
            if (arguments.length < 3) {
            	formatter = new TagFormatter("B", null);
            } else {
            	Item tagName = arguments[2].head();
            	if (tagName instanceof QNameValue) {
            		QNameValue qname = (QNameValue) tagName;
                formatter = new TagFormatter (qname.getLocalName(), qname.getNamespaceURI());
            	} else if (tagName instanceof StringValue) {
                  formatter = new TagFormatter(tagName.getStringValue(), null);
            	} else {
                  throw new XPathException ("invalid tag name for lux:highlight: got a " + tagName.getClass().getSimpleName() + " when expecting a QName or string");
            	}
            }
            XmlHighlighter xmlHighlighter = new XmlHighlighter(eval.getCompiler().getProcessor(), indexConfiguration, formatter);
            try {
                XdmNode highlighted = xmlHighlighter.highlight(query, docArg);
                return highlighted.getUnderlyingNode();
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
