/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xpath;

import lux.ExpressionVisitor;

public class Dot extends AbstractExpression {

    public Dot () {
        super (Type.Dot);
    }
    
    @Override
    public void toString(StringBuilder buf) {
        buf.append ('.');
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }

    @Override
    public int getPrecedence () {
        return 100;
    }

}
