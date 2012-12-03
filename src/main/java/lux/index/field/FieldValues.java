package lux.index.field;

import java.util.Iterator;

import lux.index.IndexConfiguration;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.document.NumericField;

public class FieldValues implements Iterable<Fieldable> {
    
    private final FieldDefinition field;
    private final String fieldName;
    private final Iterable<?> values;
    
    public FieldValues (IndexConfiguration indexConfig, FieldDefinition field, Iterable<?> values) {
        this.field = field;
        this.values = values;
        this.fieldName = indexConfig.getFieldName(field);
    }

    public Iterator<Fieldable> iterator() {
        return new FieldIterator(values.iterator());
    }
    
    class FieldIterator implements Iterator<Fieldable> {
        private Iterator<?> iter;

        FieldIterator (Iterator<?> iter) {
            this.iter = iter;
        }
        
        public boolean hasNext() {
            return iter.hasNext();
        }

        public Fieldable next() {
            // FIXME: for best indexing performance, avoid GC and re-use these Field objects:
            // can also re-use Documents.  Can only use each field instance once per document
            Object value = iter.next();
            switch (field.getType()) {
            case STRING:
                return new Field(fieldName, value.toString(), field.isStored(), field.getIndexOptions());
            case INT:
                NumericField nf = new NumericField(fieldName);
                nf.setIntValue((Integer) value);
                return nf;
            case TOKENS:
                return (Field) value;
            default:
                throw new IllegalStateException("unimplemented field type: " + field.getType());                    
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
        
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
