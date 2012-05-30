package lux.xquery;

import lux.xpath.AbstractExpression;

public class VariableDefinition implements Comparable<VariableDefinition> {
    private final AbstractExpression variable;
    private final AbstractExpression value;
    private final String typeDesc;
    private final int order;
    
    public VariableDefinition (AbstractExpression var, AbstractExpression value, String typeDesc, int order) {
        this.variable = var;
        this.value = value;
        this.typeDesc = typeDesc;
        this.order = order;
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

    public int compareTo(VariableDefinition o) {
        return order - o.order;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
