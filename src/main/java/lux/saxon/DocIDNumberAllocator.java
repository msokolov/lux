package lux.saxon;

import net.sf.saxon.tree.util.DocumentNumberAllocator;

/**
 * This class enables external docIDs to be passed to a Saxon DocBuilder via its Configuration.
 * An id must be set by calling setDocID(int) before attempting to build each document.
 */
public class DocIDNumberAllocator extends DocumentNumberAllocator {
    
    private Integer docID;
    
    public void setDocID (int id) {
        docID = id;
    }
    
    public long allocateDocumentNumber() {
        int id = docID;
        docID = null;
        return id;
    }
}
