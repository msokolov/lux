package lux.saxon;

import lux.api.QueryContext;
import net.sf.saxon.s9api.XdmItem;

public class SaxonContext extends QueryContext {

    public SaxonContext () {
    }
    
    public SaxonContext (XdmItem contextItem) {
        setContextItem(contextItem);
     }

    public void setContextItem (XdmItem item) {
        super.setContextItem(item);
    }
    
    public XdmItem getContextItem () {
        return (XdmItem) super.getContextItem();
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
