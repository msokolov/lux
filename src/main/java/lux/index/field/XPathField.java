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

/**
 * Indexes the values of the XPath expression evaluated with the document as the context item
 * @param <T> the type of value stored in or indexed by the field; must correspond with the {@link FieldDefinition.Type}:
 * STRING =&gt; String, and INT =&gt; Integer
 */
public class XPathField<T> extends FieldDefinition {
    
    private final String xpath;
    
    /**
     * create a new indexed field whose values are given by evaluating an XPath expression
     * @param name the name of the field; although Lucene naming conventions are fairly loose,
     * it will go easier if you restrict yourself to [A-Za-z0-9_.-]
     * @param xpath the xpath to evaluate
     * @param analyzer String-valued fields may be subject to further textual analysis, or pass null
     * if the field is not to be analyzed.
     * @param isStored whether the field values are stored (and may be retrieved)
     * @param type the type of data indexed by the field
     */
    public XPathField (String name, String xpath, Analyzer analyzer, Store isStored, Type type) {
        super (name, analyzer, isStored, type);
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

        @Override
        public boolean hasNext() {
            return sequence.hasNext();
        }

        @Override
        @SuppressWarnings("unchecked")
        public T next() {
            XdmItem item = sequence.next();
            switch (getType()) {
            case STRING: 
            case TEXT:
                return (T) item.getStringValue();
            case INT: return (T) Integer.valueOf (item.getStringValue());
            case LONG: return (T) Long.valueOf (item.getStringValue());
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
