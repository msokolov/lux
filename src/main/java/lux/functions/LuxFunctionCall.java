/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.functions;

import net.sf.saxon.expr.StaticProperty;
import net.sf.saxon.functions.IntegratedFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionCall;

public class LuxFunctionCall extends IntegratedFunctionCall {
    
    public LuxFunctionCall(ExtensionFunctionCall f) {
        super (f);
    }

    /**
     * Extends IFC, asserting that all of the functions in the library return ordered, peer nodesets so as to 
     * allow Saxon to optimize away document order sorting and enable lazy evaluation.
     * 
     * @return the special properties
     */
    protected int computeSpecialProperties() {
      int props = super.computeSpecialProperties();
      return props | StaticProperty.ORDERED_NODESET | StaticProperty.PEER_NODESET;
    }
}

