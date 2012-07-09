package lux.index.analysis;

import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmSequenceIterator;

class EmptyXdmIterator extends XdmSequenceIterator {

    protected EmptyXdmIterator(SequenceIterator<?> base) {
      super(base);
    }
        
    public boolean hasNext () {
      return false;
    }
        
    public XdmItem next () {
      return null;
    }
        
  }