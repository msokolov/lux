package lux.functions;

import lux.Evaluator;
import lux.xpath.FunCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;

/** 
 * <code>function lux:delete($uri as xs:string) as empty-sequence()</code>
 * <p>
 * This function deletes a document from the index at the given uri. If the special uti value "lux:/" is passed,
 * <em>all</em> documents are deleted.
 * </p>
 */
public class DeleteDocument extends ExtensionFunctionDefinition {

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "delete");
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] {
                SequenceType.SINGLE_STRING
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
        return new DeleteDocumentCall ();
    }
    
    class DeleteDocumentCall extends ExtensionFunctionCall {

        @Override
        public Sequence call(XPathContext context, Sequence[] arguments)
                throws XPathException {
            String uri = arguments[0].head().getStringValue();
            Evaluator eval = SearchBase.getEvaluator(context);
            if (uri.equals("lux:/")) {
            	// TODO: delete directories
                eval.getDocWriter().deleteAll();
            } else {
                eval.getDocWriter().delete(uri);
            }
            return EmptySequence.getInstance();
        }
        
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

