package lux.xquery;

import lux.xpath.AbstractExpression;

public class VariableDefinition {
    private final AbstractExpression variable;
    private final AbstractExpression value;
    
    public VariableDefinition (AbstractExpression abstractExpression, AbstractExpression abstractExpression2) {
        this.variable = abstractExpression;
        this.value = abstractExpression2;
    }
    
    public void toString (StringBuilder buf) {
        buf.append ("declare variable ");
        variable.toString(buf);
        buf.append (" := ");
        value.toString (buf);
        buf.append (";\n");
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
