package lux.functions;

import java.util.ArrayList;

import javax.xml.transform.TransformerException;

import lux.Evaluator;
import lux.TransformErrorListener;
import lux.xpath.FunCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.iter.UnfailingIterator;
import net.sf.saxon.value.SequenceType;

/**
 * <code>lux:transform($stylesheet as node(), $context as node(), $params as item()*) as node()</code>
 * <p>This function transforms a node with an XSLT stylesheet.  Parameters are bound
 * from the $params argument, which must be an even-length list of alternating names and values.
 * If the stylesheet produces a result that is not a single node, an error will be thrown.</p>
 */
public class Transform extends ExtensionFunctionDefinition {

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "transform");
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] {
                SequenceType.SINGLE_NODE,
                SequenceType.SINGLE_NODE,
                SequenceType.ANY_SEQUENCE
        };
    }

    @Override
    public int getMinimumNumberOfArguments() {
        return 2;
    }
    
    @Override
    public int getMaximumNumberOfArguments() {
        return 3;
    }
    
    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.SINGLE_NODE;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new TransformCall ();
    }
    
    class TransformCall extends NamespaceAwareFunctionCall {

        @Override
        public UnfailingIterator<NodeInfo> call(@SuppressWarnings("rawtypes") SequenceIterator<? extends Item>[] arguments, XPathContext context)
                throws XPathException {
            NodeInfo stylesheet = (NodeInfo) arguments[0].next();
            NodeInfo node = (NodeInfo) arguments[1].next();

            Evaluator eval = SearchBase.getEvaluator(context);
            XsltCompiler xsltCompiler = eval.getCompiler().getXsltCompiler();
            xsltCompiler.setErrorListener(eval.getErrorListener());
            try {
                // TODO: cache compiled xslt somewhere
                XsltExecutable xsltexec = xsltCompiler.compile(stylesheet);
                XsltTransformer transformer = xsltexec.load();
                transformer.setSource(node);
                transformer.setErrorListener(eval.getErrorListener());
                if (arguments.length > 2) {
                    @SuppressWarnings("rawtypes")
                    SequenceIterator<? extends Item> params = arguments[2];
                    Item<?> param;
                    while ((param = params.next()) != null) {
                        Item<?> value = params.next();
                        if (value == null) {
                            throw new XPathException ("Odd number of items in third argument to lux:transform, which should be parameter/value pairs");
                        }
                        String paramName = param.getStringValue();
                        String[] parts = paramName.split(":", 2);
                        StructuredQName sQName;
                        if (parts.length < 2) {
                            sQName = new StructuredQName("", "", paramName);
                        } else {
                            String prefix = parts[0];
                            String name = parts[1];
                            String nsURI = getNamespaceResolver().getURIForPrefix(prefix, false);
                            sQName = new StructuredQName(prefix, nsURI, name);
                        }
                        transformer.getUnderlyingController().setParameter(sQName, value);
                    }
                }
                XdmDestination dest = new XdmDestination();
                transformer.setDestination(dest);
                transformer.transform();
                ArrayList<TransformerException> runtimeErrors = ((TransformErrorListener)transformer.getErrorListener()).getErrors();
                if (!runtimeErrors.isEmpty()) {
                    throw new XPathException(runtimeErrors.get(0).getMessage(), runtimeErrors.get(0).getLocator());
                }
                return SingletonIterator.makeIterator(dest.getXdmNode().getUnderlyingNode());
            } catch (SaxonApiException e) {
                throw new XPathException (e);
            }
        }
        
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

