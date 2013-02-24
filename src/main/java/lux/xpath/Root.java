package lux.xpath;


public class Root extends AbstractExpression {
    
    public Root () {
        super (Type.ROOT);
        subs = new AbstractExpression[0];
    }
    
    @Override
    public void toString(StringBuilder buf) {
        buf.append ("(/)");
    }
    
    @Override
    public Root getRoot() {
        return this;
    }

    @Override
    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }
    
    /**
     * @return 0
     */
    @Override public int getPrecedence () {
        return 100;
    }

    /** 
     * replace this with the given expression
     * @param replacement the expression to use in place of this
     * @return the same expression
     */
    @Override
    public AbstractExpression replaceRoot(AbstractExpression replacement) {        
        return replacement;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
