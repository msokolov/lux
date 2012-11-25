package lux.functions;

import lux.saxon.Config;
import lux.saxon.Evaluator;
import lux.xpath.FunCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;

/**
 * This function inserts a document to the index at the given uri.  
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
    public ExtensionFunctionCall makeCallExpression() {
        return new InsertDocumentCall ();
    }
    
    class InsertDocumentCall extends ExtensionFunctionCall {

        @Override
        public SequenceIterator<?> call(SequenceIterator<? extends Item>[] arguments, XPathContext context)
                throws XPathException {
            String uri = arguments[0].next().getStringValue();
            NodeInfo node = (NodeInfo) arguments[1].next();
            Evaluator saxon = (Evaluator) context.getConfiguration().getCollectionURIResolver();
            // FIXME: we need to be able to get the IndexWriter from our global configuration
            // somehow.  These are thread-safe, so should be OK to just return a handle
            saxon.getSearcher().getIndexReader();
            // saxon.getIndexer().indexDocument(indexWriter, uri, xmlStream)
            return null;
        }
        
    }

}
