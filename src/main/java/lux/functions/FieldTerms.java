package lux.functions;

import java.io.IOException;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;

import lux.Evaluator;
import lux.xpath.FunCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.SequenceType;


/**
 * This function accepts the name of a Lucene field, and a starting position,
 * and returns the sequence of terms drawn from the field, ordered according
 * to its natural order, starting with the first term that is >= to the specified
 * starting position.
 * 
 * TODO: accept an indexable-sequence (restricted class of XdmItem) 
 * rather than a field name, and infer the field name from that.
 */
public class FieldTerms extends ExtensionFunctionDefinition {

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "fieldTerms");
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] {
                SequenceType.OPTIONAL_STRING,
                SequenceType.OPTIONAL_STRING
        };
    }
    
    @Override
    public int getMinimumNumberOfArguments() {
        return 0;
    }
    
    @Override
    public int getMaximumNumberOfArguments() {
        return 2;
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.ATOMIC_SEQUENCE;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new FieldTermsCall ();
    }
    
    class FieldTermsCall extends ExtensionFunctionCall {

        @Override
        public SequenceIterator<AtomicValue> call(@SuppressWarnings("rawtypes") SequenceIterator<? extends Item>[] arguments, XPathContext context)
                throws XPathException {
            String fieldName=null, start="";
            if (arguments.length > 0) {
                Item<?> arg0 = arguments[0].next();
                fieldName = arg0.getStringValue();
                if (arguments.length > 1) {
                    Item<?> arg1 = arguments[1].next();
                    start = arg1 == null ? "" : arg1.getStringValue();
                }
            }
            Evaluator saxon = (Evaluator) context.getConfiguration().getCollectionURIResolver();
            try {
                if (fieldName == null) {
                    return new TermsIterator (saxon, null);
                } else {
                    return new TermsIterator (saxon, new Term(fieldName, start));
                }
            } catch (IOException e) {
                throw new XPathException ("failed getting terms from field " + fieldName, e);
            }
        }
        
    }
    
    class TermsIterator implements SequenceIterator<AtomicValue> {
        private final TermEnum terms;
        private final Evaluator saxon;
        private Term term;
        private int pos;
        
        TermsIterator (Evaluator saxon, Term term) throws IOException {
            this.term = term;
            this.saxon = saxon;
            this.terms = createTerms(saxon, term);
            pos = 0;
        }

        private TermEnum createTerms(Evaluator saxon, Term term) throws IOException {
            if (term != null) {
                return saxon.getSearcher().getIndexReader().terms (term);
            } else {
                TermEnum terms = saxon.getSearcher().getIndexReader().terms ();
                terms.next(); // position on first term as we do when a term start position is given
                return terms;
            }
        }

        @Override
        public AtomicValue next() throws XPathException {
            try {
                Term t = terms.term();
                if (t == null || (term != null && !term.field().equals(t.field()))) {
                    pos = -1;
                    return null;
                }
                terms.next();
                ++pos;
                return new net.sf.saxon.value.StringValue (t.text());
            } catch (IOException e) {
                throw new XPathException(e);
            }
        }

        @Override
        public AtomicValue current() {
            return new net.sf.saxon.value.StringValue(terms.term().text());
        }

        @Override
        public int position() {
            return pos;
        }

        @Override
        public void close() {
            try {
                terms.close();
            } catch (IOException e) { }
        }

        @Override
        public SequenceIterator<AtomicValue> getAnother() throws XPathException {
            try {
                return new TermsIterator (saxon, term);
            } catch (IOException e) {
                throw new XPathException (e);
            }
        }

        @Override
        public int getProperties() {
            return 0;
        }
        
    }

}
