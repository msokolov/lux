package lux.xquery;

import lux.xpath.AbstractExpression;

public class VariableDefinition {
    private final AbstractExpression variable;
    private final AbstractExpression value;
    private final String typeDesc;
    
    public VariableDefinition (AbstractExpression abstractExpression, AbstractExpression abstractExpression2, String typeDesc) {
        this.variable = abstractExpression;
        this.value = abstractExpression2;
        this.typeDesc = typeDesc;
    }
    
    public void toString (StringBuilder buf) {
        buf.append ("declare variable ");
        variable.toString(buf);
        if (typeDesc != null) {
            buf.append (" as ").append(typeDesc);
        }
        if (value == null) {
            buf.append(" external;\n");
        } else {
            buf.append (" := ");
            value.toString (buf);
            buf.append (";\n");
        }
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
