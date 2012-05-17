/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.functions;

import net.sf.saxon.expr.Container;
import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.functions.IntegratedFunctionCall;
import net.sf.saxon.functions.IntegratedFunctionLibrary;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.trans.XPathException;

public class LuxFunctionLibrary extends IntegratedFunctionLibrary {

  public Expression bind(StructuredQName functionName, Expression[] staticArgs, StaticContext env, Container container) throws XPathException 
  {
      IntegratedFunctionCall ifc = (IntegratedFunctionCall) super.bind(functionName, staticArgs, env, container);
      
    if (ifc== null) {
      return null;
    }
    try {
      ExtensionFunctionCall f = ifc.getFunction();
      LuxFunctionCall fc = new LuxFunctionCall(f);
      fc.setFunctionName(functionName);
      fc.setArguments(staticArgs);
      return fc;
    } catch (Exception err) {
      throw new XPathException("Failed to create call to extension function " + functionName.getDisplayName(), err);
    }    
  }

}
