/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.saxon;

import java.util.Iterator;

import lux.api.ResultSet;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;

public class XdmResultSet implements ResultSet<XdmItem> {
    private XdmValue value;
    public XdmResultSet(XdmValue value) {
        this.value = value;
    }
    
    public XdmValue getXdmValue () {
        return value;
    }

    public Iterator<XdmItem> iterator() {
        return value.iterator();
    }

    public int size() {
        return value.size();
    }
    
}
