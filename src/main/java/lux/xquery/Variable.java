/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;
import lux.xpath.QName;

public class Variable extends AbstractExpression {
    
    private QName name;
    
    public Variable (QName qname) {
        super (Type.Variable);
        name = qname;
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }

    @Override
    public String toString() {
        return '$' + name.toString();
    }
    
    public QName getQName() {
        return name;
    }
}
