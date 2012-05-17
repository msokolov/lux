/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.saxon;

import lux.api.Context;
import lux.api.QueryContext;
import net.sf.saxon.s9api.XdmItem;

import lux.index.XmlField;
import lux.index.XmlIndexer;
import lux.lucene.LuxSearcher;

public class SaxonContext extends QueryContext implements Context {

    private XdmItem contextItem;

    public SaxonContext (LuxSearcher searcher, XmlIndexer indexer) {
       super (searcher, indexer);
    }
    public SaxonContext (LuxSearcher searcher, XmlIndexer indexer, XdmItem contextItem) {
        super (searcher, indexer);
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
