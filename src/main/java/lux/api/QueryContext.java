package lux.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import lux.xpath.QName;

public class QueryContext {
    
    private HashMap<QName, Object> variables;
    
    private Object contextItem;
    
    /**
     * bind an external variable so that it will be available in the scope of queries evaluated using this context
     * @param varName the name of the variable to bind
     * @param value the value to bind to the variable; this must be of an appropriate type for the Evaluator,
     * or null to clear any existing binding.
     */
    public void bindVariable (QName varName, Object value) {
        if (value == null) {
            variables.remove(varName);            
        } else {
            variables.put(varName, value);
        }
    }
    
    public Map<QName, Object> getVariableBindings() {
        return Collections.unmodifiableMap(variables);
    }
    
    public void setContextItem (Object contextItem) {
        this.contextItem = contextItem;
    }
    
    public Object getContextItem () {
        return contextItem;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
