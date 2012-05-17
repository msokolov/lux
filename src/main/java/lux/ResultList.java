package lux;

import java.util.ArrayList;
import java.util.Iterator;

import lux.api.ResultSet;

public class ResultList<T> extends ArrayList<T> implements ResultSet<T> {
    
    @Override public Iterator<T> iterator () {
        return new DebugIter<T> (super.iterator());
    }
    
    class DebugIter<S> implements Iterator<S> {
        Iterator<S> wrapped;
        
        DebugIter (Iterator<S> wrapped) {
            this.wrapped = wrapped;
        }
        
        public boolean hasNext() {
            return wrapped.hasNext();
        }

        public S next() {
            System.out.println ("next()");
            return wrapped.next();
        }

        public void remove() {
            wrapped.remove();
        }
        
    }
}
