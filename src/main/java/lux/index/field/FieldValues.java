package lux.index.field;

import java.util.Iterator;

import lux.index.IndexConfiguration;

import org.apache.lucene.document.Field;

public class FieldValues implements Iterable<Field> {
    
    private final FieldDefinition field;
    private final String fieldName;
    private final Iterable<?> values;
    
    public FieldValues (IndexConfiguration indexConfig, FieldDefinition field, Iterable<?> values) {
        this.field = field;
        this.values = values;
        this.fieldName = indexConfig.getFieldName(field);
    }

    public Iterator<Field> iterator() {
        return new FieldIterator(values.iterator());
    }
    
    class FieldIterator implements Iterator<Field> {
        private Iterator<?> iter;

        FieldIterator (Iterator<?> iter) {
            this.iter = iter;
        }
        
        public boolean hasNext() {
            return iter.hasNext();
        }

        public Field next() {
            Object value = iter.next();
            switch (field.getType()) {
            case STRING:
                return new Field(fieldName, value.toString(), field.isStored(), field.getIndexOptions());
            case INT:
                throw new IllegalStateException("unimplemented field type: INT");
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
