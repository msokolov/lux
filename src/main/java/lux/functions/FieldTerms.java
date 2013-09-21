package lux.functions;

import java.io.IOException;

import lux.Evaluator;
import lux.index.IndexConfiguration;
import lux.index.field.XmlTextField;
import lux.xpath.FunCall;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.*;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;

import org.apache.lucene.index.Fields;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

/**
 * <code>function lux:field-terms($field-name as xs:string?, $start as xs:string?) as xs:anyAtomicItem*</code>
 * <p>
 * This function accepts the name of a Lucene field, and a starting value, and
 * returns the sequence of terms drawn from the field, ordered according to its
 * natural order, starting with the first term that is >= the starting value.
 * </p>
 * <p>
 * If the $field-name argument is empty, the terms are drawn from the default
 * field defined by the {@link IndexConfiguration}, generally the
 * {@link XmlTextField}.
 * </p>
 */
public class FieldTerms extends ExtensionFunctionDefinition {

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("lux", FunCall.LUX_NAMESPACE, "field-terms");
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[] { SequenceType.OPTIONAL_STRING, SequenceType.OPTIONAL_STRING };
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
    public boolean trustResultType() {
        return true;
    }

    @Override
    public SequenceType getResultType(SequenceType[] suppliedArgumentTypes) {
        return SequenceType.ATOMIC_SEQUENCE;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new FieldTermsCall();
    }

    class FieldTermsCall extends ExtensionFunctionCall {

        @SuppressWarnings("rawtypes")
        @Override
        public Sequence call(XPathContext context, Sequence[] arguments) throws XPathException {
            String fieldName = null, start = "";
            if (arguments.length > 0) {
                Item arg0 = arguments[0].head();
                if (arg0 != null) {
                	fieldName = arg0.getStringValue();
                }
                if (arguments.length > 1) {
                    Item arg1 = arguments[1].head();
                    start = arg1 == null ? "" : arg1.getStringValue();
                }
            }
            Evaluator eval = SearchBase.getEvaluator(context);
            try {
                if (fieldName == null) {
                    fieldName = eval.getCompiler().getIndexConfiguration().getDefaultFieldName();
                    if (fieldName == null) {
                        return EmptySequence.getInstance();
                    }
                }
                return new LazySequence(new TermsIterator(eval, new Term(fieldName, start)));
            } catch (IOException e) {
                throw new XPathException("failed getting terms from field " + fieldName, e);
            }
        }

    }

    class TermsIterator implements SequenceIterator<AtomicValue> {
        private TermsEnum terms;
        private final Evaluator eval;
        private Term term;
        private int pos;
        private String current;

        TermsIterator(Evaluator eval, Term term) throws IOException {
            this.term = term;
            this.eval = eval;
            pos = -1;
            createTermsEnum(term);
        }

        private void createTermsEnum(Term t) throws IOException {
            String fieldName = t.field();
            // TODO: get atomic sub readers and iterate values from those 
            /*  From: http://lucene.apache.org/core/4_0_0-BETA/MIGRATE.html

                Note that the MultiFields approach entails a performance
                hit on MultiReaders, as it must merge terms/docs/positions
                on the fly. It's generally better to instead get the
                sequential readers (use oal.util.ReaderUtil) and then step
                through those readers yourself, if you can (this is how
                Lucene drives searches).
            */
            Fields fields = MultiFields.getFields(eval.getSearcher().getIndexReader());
            if (fields != null) {
                Terms fieldTerms = fields.terms(fieldName);
                if (fieldTerms != null) {
                    terms = fieldTerms.iterator(null);
                    if (t != null) {
                        if (terms.seekCeil(new BytesRef(t.text().getBytes("utf-8"))) != TermsEnum.SeekStatus.END) {
                            current = terms.term().utf8ToString();
                        }
                    }
                }
            }
        }

        @Override
        public AtomicValue next() throws XPathException {
            try {
                if (current == null) {
                    return null;
                }
                String value = current;
                BytesRef bytesRef = terms.next();
                if (bytesRef == null) {
                    pos = -1;
                    current = null;
                } else {
                    ++pos;
                    current = bytesRef.utf8ToString();
                }
                return new net.sf.saxon.value.StringValue(value);
            } catch (IOException e) {
                throw new XPathException(e);
            }
        }

        @Override
        public AtomicValue current() {
            return new net.sf.saxon.value.StringValue(current);
        }

        @Override
        public int position() {
            return pos;
        }

        @Override
        public void close() {
        }

        @Override
        public SequenceIterator<AtomicValue> getAnother() throws XPathException {
            try {
                return new TermsIterator(eval, term);
            } catch (IOException e) {
                throw new XPathException(e);
            }
        }

        @Override
        public int getProperties() {
            return 0;
        }

    }

}

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
