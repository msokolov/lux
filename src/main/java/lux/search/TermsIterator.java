package lux.search;

import java.io.IOException;

import lux.Evaluator;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.AtomicValue;
import net.sf.saxon.value.StringValue;

import org.apache.lucene.index.Fields;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.util.BytesRef;

/**
 * Retrieves terms from the Lucene index directly, using TermsEnum.
 */
public class TermsIterator implements SequenceIterator<AtomicValue> {
    private TermsEnum terms;
    private final Evaluator eval;
    private Term term;
    private int pos;
    private String current;
    private String next;

    TermsIterator(Evaluator eval, Term term) throws IOException {
        this.term = term;
        this.eval = eval;
        pos = 0;
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
                        next = terms.term().utf8ToString();
                    }
                }
            }
        }
    }

    @Override
    public AtomicValue next() throws XPathException {
        try {
            if (next == null) {
                pos = -1;
                return null;
            }
            ++pos;
            current = next;
            BytesRef bytesRef = terms.next();
            if (bytesRef == null) {
                next = null;
            } else {
                next = bytesRef.utf8ToString();
            }
            return new StringValue(current);
        } catch (IOException e) {
            throw new XPathException(e);
        }
    }

    @Override
    public AtomicValue current() {
        return new StringValue(current);
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

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
