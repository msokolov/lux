package lux.saxon;

import lux.api.QueryContext;
import lux.index.XmlField;
import net.sf.saxon.s9api.XdmItem;

public class SaxonContext extends QueryContext {

    private XdmItem contextItem;

    public SaxonContext () {
    }
    
    public SaxonContext (XdmItem contextItem) {
        this.contextItem = contextItem;
     }

    public String getXmlFieldName() {
        return XmlField.XML_STORE.getName();
    }
    
    public void setContextItem (XdmItem item) {
        contextItem = item;
    }
    
    public XdmItem getContextItem () {
        return contextItem;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
