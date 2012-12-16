package lux.index.field;

import java.util.Iterator;

import lux.exception.LuxException;
import lux.index.XmlIndexer;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmSequenceIterator;
import net.sf.saxon.s9api.XdmValue;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;

/**
 * Indexes the values of the XPath expression evaluated with the document as the context item
 * T is the type of the values returned and must correspond with the {@link FieldDefinition.Type}:
 * STRING =&gt; String, and INT =&gt; Integer.
 */
public class XPathField<T> extends FieldDefinition {
    
    private final String xpath;
    
    public XPathField (String name, String xpath, Analyzer analyzer, Store isStored, Type type) {
        super (name, analyzer, isStored, type, TermVector.NO);
        this.xpath = xpath;
    }
    
    @Override
    public Iterable<T> getValues(XmlIndexer indexer) {
        XdmValue value;
        try {
            value = indexer.evaluateXPath (xpath);
        } catch (SaxonApiException e) {
            throw new LuxException("error getting values for field: " + getDefaultName(), e);
        }
        return new XPathValueIterator(value.iterator());
    }

    class XPathValueIterator implements Iterator<T>, Iterable<T> {
        private final XdmSequenceIterator sequence;

        XPathValueIterator (XdmSequenceIterator sequence) {
            this.sequence = sequence;
        }

        public boolean hasNext() {
            return sequence.hasNext();
        }

        @SuppressWarnings("unchecked")
        public T next() {
            XdmItem item = sequence.next();
            switch (getType()) {
            case STRING: return (T) item.getStringValue();
            case INT: return (T) Integer.valueOf (item.getStringValue());
            default: throw new IllegalStateException (getType() + " is not a valid type for an XPathField");
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterator<T> iterator() {
            return this;
        }
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
