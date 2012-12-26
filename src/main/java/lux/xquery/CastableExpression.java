/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;


public class CastableExpression extends AbstractExpression {
        
    private String typeName;
    
    public CastableExpression (AbstractExpression op1, String typeName) {
        super (Type.CASTABLE);
        subs = new AbstractExpression[] { op1 };
        this.typeName = typeName;
    }
    
    @Override
    public void toString (StringBuilder buf) {
        appendSub(buf, subs[0]);
        buf.append(" castable as ").append (typeName);
    }

    @Override
    public AbstractExpression accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        return visitor.visit(this);
    }

    @Override
    public int getPrecedence () {
        return 14;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
