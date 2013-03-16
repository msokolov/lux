package lux.xquery;

import lux.xml.QName;
import lux.xml.ValueType;
import lux.xpath.AbstractExpression;
import lux.xpath.FunCall;

public class FunctionDefinition extends FunCall {

    private final AbstractExpression body;
    private final int cardinality;
    
    public FunctionDefinition (QName name, ValueType returnType, int cardinality, Variable[] args, AbstractExpression body) {
        super (name, returnType, args);
        this.body = body;
        this.cardinality = cardinality;
    }
    
    @Override public void toString (StringBuilder buf) {
        buf.append ("declare function ");
        super.toString(buf);
        if (getReturnType() != null) {
            buf.append (" as ").append(getReturnType().toString()).append(ValueType.CARDINALITY_MARKER[cardinality]).append(" ");
        }
        buf.append ("{ ");
        body.toString (buf);
        buf.append (" };\n");
    }

    public AbstractExpression getBody() {
        return body;
    }

    public int getCardinality() {
        return cardinality;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
