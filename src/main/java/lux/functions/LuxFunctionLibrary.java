package lux.functions;

import net.sf.saxon.expr.Container;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.functions.IntegratedFunctionCall;
import net.sf.saxon.functions.IntegratedFunctionLibrary;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;

/**
 * Extension of Saxon's function library wraps ExtensionFunctionCall in LuxFunctionCall, enabling
 * Lux to override some of the function's properties.
 */
public class LuxFunctionLibrary extends IntegratedFunctionLibrary {

    @Override
    public Expression bind(StructuredQName functionName, int arity, Expression[] staticArgs, StaticContext env, Container container)
            throws XPathException {
        IntegratedFunctionCall ifc = (IntegratedFunctionCall) super.bind(functionName, arity, staticArgs, env, container);
        if (ifc == null) {
            return null;
        }
        ExtensionFunctionCall f = ifc.getFunction();
        LuxFunctionCall fc = new LuxFunctionCall(f);
        fc.setFunctionName(functionName);
        fc.setArguments(staticArgs);
        return fc;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
