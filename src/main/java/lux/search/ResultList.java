package lux.search;

import java.util.ArrayList;
import java.util.Iterator;

import lux.api.ResultSet;

public class ResultList<T> extends ArrayList<T> implements ResultSet<T> {
    
    private Exception ex;
    
    @Override public Iterator<T> iterator () {
        return new DebugIter<T> (super.iterator());
    }
    
    public void setException (Exception ex) {
        this.ex = ex;
    }
    
    public Exception getException () {
        return ex;
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
            return wrapped.next();
        }

        public void remove() {
            wrapped.remove();
        }
        
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
