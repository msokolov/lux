package lux.functions;

import net.sf.saxon.s9api.ExtensionFunction;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.SequenceType;
import net.sf.saxon.s9api.XdmValue;

/**
 * Convenience class for defining extension functions
 */
public abstract class Function implements ExtensionFunction {
    
    private final QName qname;
    private final SequenceType resultType;
    private final SequenceType[] argumentTypes;
    
    public Function (QName qname, SequenceType resultType, SequenceType[] argumentTypes) {
        this.qname = qname;
        this.resultType = resultType;
        this.argumentTypes = argumentTypes;
    }

    @Override
    public QName getName() {
        return qname;
    }

    @Override
    public SequenceType getResultType() {
        return resultType;
    }

    @Override
    public SequenceType[] getArgumentTypes() {
       return argumentTypes;
    }
    
    public abstract XdmValue call(XdmValue[] arguments) throws SaxonApiException;
    
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
