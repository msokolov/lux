package lux;

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
    
    private ThreadLocal<Integer> nextThreadDocId = new ThreadLocal<Integer>();
  
    /**
     * It is the caller's responsibility to ensure that the same id is not assigned to multiple different documents, 
     * IDs must be assigned to documents in such a way that a consistent document ordering is maintained.
     * In order to ensure thread safety, a separate id is maintained for each calling thread.  
     * @param id the next id to allocate for the calling thread, or null if the next id to allocate should be an internal id.
     */
    public void setNextDocID (Integer id) {
        nextThreadDocId.set (id);
    }
    
    @Override
    public long allocateDocumentNumber() {
        long id;
        Integer nextDocID = nextThreadDocId.get();
        if (nextDocID != null) {
            id = nextDocID;
            nextThreadDocId.set(null);
        } else {
            id = nextInternalID++;
        }
        return id;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
