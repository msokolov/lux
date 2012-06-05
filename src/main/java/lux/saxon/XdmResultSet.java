package lux.saxon;

import java.util.Iterator;

import lux.api.ResultSet;
import net.sf.saxon.s9api.XdmEmptySequence;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;

public class XdmResultSet implements ResultSet<XdmItem> {
    
    private final XdmValue value;
    private final Exception ex;
    
    public XdmResultSet(XdmValue value) {
        this.value = value;
        ex = null;
    }
    
    public XdmResultSet(Exception ex) {
        this.value = XdmEmptySequence.getInstance();
        Exception cause = ex;
        while (cause != null && cause.getCause() != cause && (cause.getCause() instanceof Exception)) {
            cause = (Exception) cause.getCause();
        }
        this.ex = cause;
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

    public Exception getException() {
        return ex;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
