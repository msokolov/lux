package lux.functions;

import lux.xpath.FunCall;
import net.sf.saxon.om.StructuredQName;

/**
* @deprecated This function has been renamed <code>lux:key()</code> as of release 0.10.2 and will be dropped
* in subsequent major releases.
*/
@Deprecated
public class FieldValues extends Key {

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName ("lux", FunCall.LUX_NAMESPACE, "field-values");
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
