package lux.saxon;

import net.sf.saxon.tree.util.DocumentNumberAllocator;

/**
 * We guarantee that lux:search returns result in document order, which to Saxon means that
 * the ids of the documents it returns must be in increasing order.  lux:search gathers
 * docIDs in increasing order and calls setNextDocID(int) in order to maintain the same numbering 
 * scheme in Saxon.  Internally-generated documents are allocated ids starting at Integer.MAX_VALUE+1 -
 * Lucene ids are always ints.
 *
 */
public class DocIDNumberAllocator extends DocumentNumberAllocator {
    
    private long nextInternalID = Integer.MAX_VALUE+1;
    
    private Integer nextDocID;
  
    /**
     * It is the caller's responsibility to ensure that the ids passed to this method are never
     * repeated, and that they are assigned to documents in such a way that any functions declaring
     * their results to be in document order have that assertion borne out.  The caller must also ensure
     * that a document will be allocated immediately after this method is called.  For this reason,
     * this entire class is *not* thread-safe.
     * @param id the next id to allocate.
     */
    public void setNextDocID (int id) {
        nextDocID = id;
    }
    
    public long allocateDocumentNumber() {
        long id;
        if (nextDocID != null) {
            id = nextDocID;
            nextDocID = null;
        } else {
            id = nextInternalID--;
        }
        return id;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
