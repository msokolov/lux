package lux.index.field;

import java.util.Iterator;

import lux.index.IndexConfiguration;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
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

    @Override
    public Iterator<Fieldable> iterator() {
        return new FieldIterator(values.iterator());
    }
    
    class FieldIterator implements Iterator<Fieldable> {
        private Iterator<?> iter;

        FieldIterator (Iterator<?> iter) {
            this.iter = iter;
        }
        
        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public Fieldable next() {
            Object value = iter.next();
            switch (field.getType()) {
            
            case BYTES:
                if (value instanceof byte[]) {
                    return new Field(fieldName, (byte[])value);
                }
                // else fall through and treat as String?
            
            case STRING:
                return new Field(fieldName, value.toString(), field.isStored(), Index.NOT_ANALYZED);
            
            case INT: {
                NumericField nf = new NumericField(fieldName, field.isStored(), true);
                nf.setIntValue((Integer) value);
                return nf;
            }
            
            case LONG: {
                NumericField nf = new NumericField(fieldName, field.isStored(), true);
                nf.setLongValue((Long) value);
                return nf;
            }
            
            case TEXT:
                return new Field (fieldName, value.toString(), field.isStored(), Index.ANALYZED);
                
            case TOKENS:
                return (Fieldable) value;

            default:
                throw new IllegalStateException("unimplemented field type: " + field.getType());                    
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
        
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
