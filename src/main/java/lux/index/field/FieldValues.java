package lux.index.field;

import java.util.Iterator;


import org.apache.lucene.document.Field;
import org.apache.lucene.document.Fieldable;

public class FieldValues implements Iterable<Fieldable> {
    
    private XmlField field;
    private Iterable<?> values;
    
    public FieldValues (XmlField field, Iterable<?> values) {
        this.field = field;
        this.values = values;
    }

    public Iterator<Fieldable> iterator() {
        return new FieldableIterator(values.iterator());
    }
    
    class FieldableIterator implements Iterator<Fieldable> {
        private Iterator<?> iter;

        FieldableIterator (Iterator<?> iter) {
            this.iter = iter;
        }
        
        public boolean hasNext() {
            return iter.hasNext();
        }

        public Fieldable next() {
            Object value = iter.next();
            switch (field.getType()) {
            case STRING:
                return new Field(field.getName(), value.toString(), field.isStored(), field.getIndexOptions());
            case INT:
                throw new IllegalStateException("unimplemented field type: INT");
            case TOKENS:
                return (Fieldable) value;
            default:
                    throw new IllegalStateException("unimplemented field type: " + field.getType());                    
            }
        }

        public void remove() {
            // TODO Auto-generated method stub
            
        }
        
    }

}
