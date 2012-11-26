package lux.functions;

import lux.Evaluator;
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
 * This function inserts a document to the index at the given uri.  
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
        return new InsertDocumentCall ();
    }
    
    class InsertDocumentCall extends ExtensionFunctionCall {

        @Override
        public SequenceIterator<?> call(@SuppressWarnings("rawtypes") SequenceIterator<? extends Item>[] arguments, XPathContext context)
                throws XPathException {
            String uri = arguments[0].next().getStringValue();
            Evaluator eval = (Evaluator) context.getConfiguration().getCollectionURIResolver();
            eval.getDocWriter().delete(uri);
            return EmptySequence.asIterator(EmptySequence.getInstance());
        }
        
    }

}
