package lux.functions;

import lux.Evaluator;
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
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.XsltTransformer;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.iter.SingletonIterator;
import net.sf.saxon.tree.iter.UnfailingIterator;
import net.sf.saxon.value.SequenceType;

/**
 * <code>lux:transform($stylesheet as node(), $context as node()) as node()
 * <p>This function transforms a node with an XSLT stylesheet.  If the stylesheet produces
 * a result that is not a single node, an error will be thrown.</p>
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
                SequenceType.SINGLE_NODE
        };
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.SINGLE_NODE;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new TransformCall ();
    }
    
    class TransformCall extends ExtensionFunctionCall {

        @Override
        public UnfailingIterator<NodeInfo> call(@SuppressWarnings("rawtypes") SequenceIterator<? extends Item>[] arguments, XPathContext context)
                throws XPathException {
            NodeInfo stylesheet = (NodeInfo) arguments[0].next();
            NodeInfo node = (NodeInfo) arguments[1].next();
            Evaluator eval = (Evaluator) context.getConfiguration().getCollectionURIResolver();
            try {
                // TODO: cache compiled xslt somewhere
                // TODO: accept and pass on parameter bindings
                XsltExecutable xsltexec = eval.getCompiler().getXsltCompiler().compile(stylesheet);
                XsltTransformer transformer = xsltexec.load();
                transformer.setSource(node);
                XdmDestination dest = new XdmDestination();
                transformer.setDestination(dest);
                transformer.transform();
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

