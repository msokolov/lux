package lux.saxon;

import java.util.Collection;
import java.util.Iterator;

import javax.xml.transform.TransformerException;

import net.sf.saxon.s9api.XdmEmptySequence;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;

public class XdmResultSet implements Iterable<XdmItem> {
    
    private final XdmValue value;
    private final Collection<TransformerException> errors;
    
    public XdmResultSet(XdmValue value) {
        this.value = value;
        errors = null;
    }
    
    public XdmResultSet (Collection<TransformerException> errors) {
        this.value = XdmEmptySequence.getInstance();
        this.errors = errors;
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

    public Collection<TransformerException> getErrors() {
        return errors;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
