package lux.functions;

import lux.Evaluator;
import lux.Evaluator.LuxCollectionURIResolver;
import lux.xpath.FunCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;

/**
 * <code>function lux:commit() as empty-sequence()</code>
 * <p>Commits pending updates to the index and blocks until the operation is complete.</p>
 */
public class Commit extends ExtensionFunctionDefinition {

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "commit");
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] { };
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
        return new CommitCall ();
    }
    
    class CommitCall extends ExtensionFunctionCall {

        @Override
        public SequenceIterator<?> call(@SuppressWarnings("rawtypes") SequenceIterator<? extends Item>[] arguments, XPathContext context)
                throws XPathException {
            LuxCollectionURIResolver resolver = (Evaluator.LuxCollectionURIResolver) context.getConfiguration().getCollectionURIResolver();
            Evaluator eval = resolver.getEvaluator();
            eval.getDocWriter().commit();
            eval.reopenSearcher();
            return EmptySequence.asIterator(EmptySequence.getInstance());
        }
        
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

