package lux.xquery;

import lux.xpath.AbstractExpression;

public abstract class VariableBindingClause extends FLWORClause implements VariableContext{

    private Variable var;
    private AbstractExpression seq;

    public VariableBindingClause(Variable var, AbstractExpression seq) {
        this.var = var;
        this.seq = seq;
    }

    public Variable getVariable() {
        return var;
    }

    @Override
    public AbstractExpression getSequence() {
        return seq;
    }

    @Override
    public void setSequence(AbstractExpression seq) {
        this.seq = seq;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
