package lux.index.analysis;

import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmSequenceIterator;

class EmptyXdmIterator extends XdmSequenceIterator {

    protected EmptyXdmIterator(SequenceIterator<?> base) {
      super(base);
    }
        
    @Override
    public boolean hasNext () {
      return false;
    }
        
    @Override
    public XdmItem next () {
      return null;
    }
        
  }

/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/.
 */
