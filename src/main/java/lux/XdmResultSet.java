package lux;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.xml.transform.TransformerException;

import net.sf.saxon.s9api.XdmEmptySequence;
import net.sf.saxon.s9api.XdmItem;
import net.sf.saxon.s9api.XdmValue;

/**
 * Represents the result of a query evaluation.  This will contain either an XdmValue, 
 * or if there was an error, a list of Exceptions.  The class 
 * will never return null. If there were errors, the value will be
 * an empty sequence.  If there were no errors, there will be an empty error list.  
 */
public class XdmResultSet implements Iterable<XdmItem> {
    
    private final XdmValue value;
    private final List<TransformerException> errors;
    
    public XdmResultSet(XdmValue value) {
        this.value = value;
        errors = Collections.emptyList();
    }
    
    public XdmResultSet (List<TransformerException> errors) {
        this.value = XdmEmptySequence.getInstance();
        this.errors = errors;
    }

    /**
     * @return the result of the query evaluation, as an {@link XdmValue}.
     */
    public XdmValue getXdmValue () {
        return value;
    }
    
    /**
     * @return the result of the query evaluation, as an {@link XdmItem} iterator.
     */
    public Iterator<XdmItem> iterator() {
        return value.iterator();
    }

    public int size() {
        return value.size();
    }

    /**
     * @return the list of errors reported when compiling or evaluating an xquery expression.
     * returns an empty list if no errors were generated.
     */
    public List<TransformerException> getErrors() {
        return errors;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
