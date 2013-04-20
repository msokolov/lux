package lux.functions;

import java.util.ArrayList;

import javax.xml.transform.TransformerException;

import lux.Evaluator;
import lux.TransformErrorListener;
import lux.xpath.FunCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.*;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XQueryCompiler;
import net.sf.saxon.s9api.XQueryEvaluator;
import net.sf.saxon.s9api.XQueryExecutable;
import net.sf.saxon.s9api.XdmValue;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;

/**
 * <code>lux:eval($query as xs:string, $params as item()*) as item()*</code>
 * <p>This function evaluates an XQuery expression.  Parameters are bound
 * from the $params argument, which must be an even-length list of alternating names and values.
 * </p>
 */
public class Eval extends ExtensionFunctionDefinition {

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "eval");
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] {
                SequenceType.SINGLE_STRING,
                SequenceType.ANY_SEQUENCE
        };
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
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.ANY_SEQUENCE;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new EvalCall ();
    }
    
    class EvalCall extends InterpreterCall {

        private XQueryEvaluator evaluator;
        
        @Override
        public Sequence call(XPathContext context, Sequence[] arguments)
                throws XPathException {
            String query = arguments[0].head().getStringValue();
            Evaluator eval = SearchBase.getEvaluator(context);
            XQueryCompiler xqueryCompiler = eval.getCompiler().getXQueryCompiler();
            xqueryCompiler.setErrorListener(eval.getErrorListener());
            try {
                // TODO: cache compiled xslt somewhere
                XQueryExecutable xqueryExec= xqueryCompiler.compile(query);
                evaluator = xqueryExec.load();
                evaluator.setErrorListener(eval.getErrorListener());
                if (arguments.length > 1) {
                    bindParameters(arguments[1]);
                }
                XdmValue result = evaluator.evaluate();
                ArrayList<TransformerException> runtimeErrors = eval.getErrorListener().getErrors();
                if (!runtimeErrors.isEmpty()) {
                    throw new XPathException(runtimeErrors.get(0).getMessage(), runtimeErrors.get(0).getLocator(), runtimeErrors.get(0));
                }
                return result.getUnderlyingValue();
            } catch (SaxonApiException e) {
                throw new XPathException (e);
            }
        }

        @Override
        protected void setParameter(StructuredQName name, Item value) {
            evaluator.getUnderlyingQueryContext().setParameterValue (name.getClarkName(), value);
        }

    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
