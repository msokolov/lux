package lux.xquery;

import lux.ExpressionVisitor;
import lux.xpath.AbstractExpression;

public class WhereClause extends FLWORClause {

    private AbstractExpression predicate;
    
    public WhereClause(AbstractExpression predicate) {
        this.predicate = predicate;
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ("where ");
        predicate.toString(buf);
    }

    public AbstractExpression accept(ExpressionVisitor visitor) {
        predicate = predicate.accept(visitor);
        return predicate;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
