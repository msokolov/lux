package lux.functions;

import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.functions.IntegratedFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionCall;

/**
 * Asserts that all of the functions in the library return ordered, peer nodesets so as to 
 * allow Saxon to optimize away document order sorting and enable lazy evaluation.
 * TODO: specialize so it is possible to write functions for which this is not true without
 * making a false assertion here.
 */
public class LuxFunctionCall extends IntegratedFunctionCall {
    
    public LuxFunctionCall(ExtensionFunctionCall f) {
        super (f);
    }

    /**
     * @return any existing special properties, joined by ORDER_NODESET and PEER_NODESET
     */
    @Override
    protected int computeSpecialProperties() {
      int props = super.computeSpecialProperties();
      return props | StaticProperty.ORDERED_NODESET | StaticProperty.PEER_NODESET;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
