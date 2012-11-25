package lux.xquery;

import lux.xpath.AbstractExpression;
import lux.xpath.ExpressionVisitor;

public class AttributeConstructor extends AbstractExpression {
    
    public AttributeConstructor(AbstractExpression name, AbstractExpression content) {
        super(Type.ATTRIBUTE);
        subs = new AbstractExpression[] { name, content };
    }

    public AbstractExpression accept(ExpressionVisitor visitor) {
        acceptSubs(visitor);
        return visitor.visit(this);
    }

    @Override
    public void toString(StringBuilder buf) {
        buf.append ("attribute ");
        buf.append ("{ ");
        getName().toString (buf);        
        buf.append (" } { ");
        appendValue (buf);
        buf.append (" }");
    }
    
    public void appendValue (StringBuilder buf) {
        // This works around a test in the XQTS, but it seems broken: what if some code contains
        // newlines?  I think it's OK? What happens is that "attribute whitespace normalization" will have
        // converted literal CR LF characters to spaces.  The only way we should be seeing these characters
        // here is if they were originally provided as character references.        
        String c = getContent().toString ();
        c = c.replace ("\r", "&#xD;").replace("\n", "&#xA;");
        buf.append (c);        
    }

    public final AbstractExpression getName () {
        return subs[0];
    }

    public final AbstractExpression getContent () {
        return subs[1];
    }

    @Override
    public int getPrecedence () {
        return 0;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
