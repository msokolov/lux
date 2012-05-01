package lux.functions;

import lux.xpath.FunCall;
import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.DocumentInfo;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.pattern.NodeKindTest;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.SequenceType;

/**
 * lux:root($sequence) works like root($sequence); it returns only
 * a single instance of each document in $sequence, assuming the nodes in 
 * $sequence are sorted in document order.  This is needed because Saxon's root()
 * may return multiple copies of the same document; these are typically made unique
 * by a documentSorter, but because of the sorting requirement, the entire sequence
 * must be retrieved before any results are made available to the consumer.
 * 
 */
public class LuxRoot extends ExtensionFunctionDefinition {
    
    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "root");
    }
    
    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] { SequenceType.NODE_SEQUENCE };
    }
    
    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.makeSequenceType(NodeKindTest.DOCUMENT, StaticProperty.ALLOWS_ZERO_OR_MORE);
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new LuxRootCall ();
    }
    
    @SuppressWarnings("rawtypes")
    class LuxRootCall extends ExtensionFunctionCall {

        @Override
        public SequenceIterator<? extends Item> call( SequenceIterator<? extends Item>[] arguments, XPathContext context)
                throws XPathException {
            return new UniqueDocumentIterator(arguments[0]);
        }
        
    }
    
    @SuppressWarnings("rawtypes")
    class UniqueDocumentIterator implements SequenceIterator<DocumentInfo> {
        
        private final SequenceIterator<? extends Item> sequence;
        private DocumentInfo current;
        private int pos;
        
        UniqueDocumentIterator (SequenceIterator<? extends Item> sequence) {
            this.sequence = sequence;
            this.current = null;
            this.pos = 0;
        }

        public DocumentInfo next() throws XPathException {
            DocumentInfo document;
            do {
                NodeInfo node = (NodeInfo) sequence.next();
                if (node == null) {
                    document = null;
                    break;
                }
                document = node.getDocumentRoot();
            } while (document == current);
            if (current != document) {
                ++pos;
                current = document;
            }
            return current;
        }

        public DocumentInfo current() {
            return current;
        }

        public int position() {
            return pos;
        }

        public void close() {
            sequence.close();
        }

        public UniqueDocumentIterator getAnother() throws XPathException {
            return new UniqueDocumentIterator(sequence.getAnother());
        }

        public int getProperties() {
            return 0;
        }
        
    }
}
