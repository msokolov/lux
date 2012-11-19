package lux.xquery;

import lux.compiler.ExpressionVisitor;
import lux.xpath.AbstractExpression;

public class FLWOR extends AbstractExpression {
    
    private final FLWORClause[] clauses;

    public FLWOR (AbstractExpression returnExpression, FLWORClause... clauses) {
        super (Type.FLWOR);
        this.clauses = clauses;
        subs = new AbstractExpression[] { returnExpression };
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        for (FLWORClause clause : clauses) {
            clause.accept (visitor);
        }
        subs[0] = getReturnExpression().accept(visitor);
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        boolean inWhereClause = false;
        for (FLWORClause clause : clauses) {
            if (clause instanceof WhereClause) {
                // combine Saxon's adjacent where clauses into a single one
                if (inWhereClause) {
                    buf.append ("and ");
                    clause.getSequence().toString(buf);
                } else {
                    inWhereClause = true;
                    clause.toString(buf);
                }
            } else {
                clause.toString(buf);
            }
            buf.append(' ');
        }
        buf.append ("return ");
        getReturnExpression().toString(buf);
    }
    
    public AbstractExpression getReturnExpression () {
        return subs[0];
    }
    
    public FLWORClause[] getClauses () {
        return clauses;
    }
    
    public boolean isAbsolute () {
        return getReturnExpression().isAbsolute();
    }

    // TODO: are we confused here? Spec lists different precedence (2)
    // for assignment operator?
    @Override
    public int getPrecedence () {
        return 3;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
