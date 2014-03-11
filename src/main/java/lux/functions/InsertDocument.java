package lux.functions;

import lux.Evaluator;
import lux.xpath.FunCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.type.Type;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;

/**
 * <code>function lux:insert($uri as xs:string, $node as node()) as empty-sequence()</code>
 * <p>inserts a document to the index at the given uri.  lux:commit() must be called for the result
 * to become visible.</p>
 */
public class InsertDocument extends ExtensionFunctionDefinition {

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "insert");
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] {
                SequenceType.SINGLE_STRING,
                SequenceType.SINGLE_NODE
        };
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.EMPTY_SEQUENCE;
    }

    @Override
    public boolean hasSideEffects () {
        return true;
    }
    
    @Override
    public boolean trustResultType () {
        return true;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new InsertDocumentCall ();
    }
    
    class InsertDocumentCall extends ExtensionFunctionCall {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments)
                throws XPathException {
            String uri = arguments[0].head().getStringValue();
            NodeInfo node = (NodeInfo) arguments[1].head();
            if (node.getNodeKind() == Type.TEXT) {
                // TODO: wrap this in a document and insert it, sure
                throw new XPathException ("Attempt to insert text node: not supported");
            }
            if (! (node.getNodeKind() == Type.DOCUMENT || node.getNodeKind() == Type.ELEMENT)) {
                throw new XPathException ("Attempt to insert unsupported node type");
            }
            Evaluator eval = SearchBase.getSearchService(context).getEvaluator();
            try {
                eval.getDocWriter().write(node, uri);
            } catch (Exception e) {
                throw new XPathException ("An error occurred while inserting a document at uri: " + uri + 
                        "; " + e.getMessage(), e);
            }
            return EmptySequence.getInstance();
        }
        
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
