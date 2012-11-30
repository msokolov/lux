package lux;

import java.util.Iterator;
import java.util.List;

import javax.xml.transform.TransformerException;

import net.sf.saxon.s9api.XdmEmptySequence;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;

public class XdmResultSet implements Iterable<XdmItem> {
    
    private final XdmValue value;
    private final List<TransformerException> errors;
    
    public XdmResultSet(XdmValue value) {
        this.value = value;
        errors = null;
    }
    
    public XdmResultSet (List<TransformerException> errors) {
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

    /**
     * @return the list of errors reported when compiling or evaluating an xquery expression.
     * returns null if no errors were generated.
     */
    public List<TransformerException> getErrors() {
        return errors;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
