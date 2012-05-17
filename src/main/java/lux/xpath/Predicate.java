/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xpath;

public class Predicate extends AbstractExpression {
    
    public Predicate (AbstractExpression base, AbstractExpression filter) {
        super (Type.Predicate);
        subs = new AbstractExpression[] { base, filter };
    }
    
    public String toString () {
        return subs[0].toString() + '[' + subs[1].toString() + ']';
    }
    
    public final AbstractExpression getBase() {
        return subs[0];
    }

    public final AbstractExpression getFilter() {
        return subs[1];
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        return visitor.visit(this);
    }
    
    @Override    
    public boolean isAbsolute () {
        return getBase().isAbsolute();
    }
    
    @Override
    public boolean isDocumentOrdered () {
        return getBase().isDocumentOrdered();
    }

}
