package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;

public class FLWOR extends AbstractExpression {
    
    private final FLWORClause[] clauses;

    public FLWOR (AbstractExpression returnExpression, FLWORClause... clauses) {
        super (Type.FLWOR);
        this.clauses = clauses;
        subs = new AbstractExpression[] { returnExpression };
    }
    
    // (return (let (for sequence (where) (order by))))
    @Override
    public AbstractExpression accept(ExpressionVisitor visitor) {
        for (int i = 0; i < clauses.length; i++) {
            // accept in forward order since queries get reversed on the stack.
            // This leaves the deepest query at the top of the stack (last in),
            // so it can be popped out first as we unwind in visit()
            clauses[i].accept (visitor);
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
            buf.append("\n ");
        }
        buf.append ("return ");
        getReturnExpression().toString(buf);
        buf.append ("\n");
    }
    
    public AbstractExpression getReturnExpression () {
        return subs[0];
    }
    
    public FLWORClause[] getClauses () {
        return clauses;
    }
    
    @Override
    public AbstractExpression getRoot () {
        return getReturnExpression().getRoot();
    }

    // TODO: are we confused here? Spec lists different precedence (2)
    // for assignment operator, but something failed when we did that?
    @Override
    public int getPrecedence () {
        return 3;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
