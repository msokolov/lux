package lux.index.field;

import java.util.Iterator;

import org.apache.lucene.document.IntField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexableField;

import lux.index.FieldRole;

public class FieldValues implements Iterable<IndexableField> {
    
    private final FieldDefinition field;
    private final String fieldName;
    private final Iterable<?> values;
    
    public FieldValues (FieldDefinition field, Iterable<?> values) {
        this.field = field;
        this.values = values;
        this.fieldName = field.getName();
    }

    @Override
    public Iterator<IndexableField> iterator() {
        return new FieldIterator(values.iterator());
    }
    
    class FieldIterator implements Iterator<IndexableField> {
        private Iterator<?> iter;

        FieldIterator (Iterator<?> iter) {
            this.iter = iter;
        }
        
        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }

        @Override
        public IndexableField next() {
            Object value = iter.next();
            if (value instanceof IndexableField) {
                return (IndexableField) value;
            }
            switch (field.getType()) {
            case BYTES:
                if (value instanceof byte[]) {
                    return new StoredField(fieldName, (byte[])value);
                }
                // else fall through and treat as String?
            case STRING:
                if (fieldName.equals(FieldRole.XML_STORE.getFieldName())) {
                    return new StoredField(fieldName, value.toString());
                }
                return new StringField(fieldName, value.toString(), field.isStored());
                
            case TEXT:
                return new TextField (fieldName, value.toString(), field.isStored());
                
            case INT:
                return new IntField(fieldName, ((Integer)value).intValue(), field.isStored());

            case LONG:
                return new LongField(fieldName, ((Long)value).longValue(), field.isStored());
                /*
            case TOKENS:
                return (IndexableField) value;
                
            case SOLR_FIELD:
                return (IndexableField) value;
                 */
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
