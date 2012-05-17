/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xpath;

public class Sequence extends AbstractExpression {
    
    public Sequence (AbstractExpression ... contents) {
        super (Type.Sequence);
        subs = contents;
    }
    
    @Override
    public String toString() {
        return seqAsString(",", subs);
    }
    
    static final String seqAsString (String separator, AbstractExpression ... contents) {
        StringBuilder buf = new StringBuilder ();
        buf.append('(');
        appendSeq(buf, contents, separator);
        buf.append (')');
        return buf.toString();
    }

    static boolean appendSeq(StringBuilder buf, AbstractExpression[] contents, String separator) {
        boolean first = true;
        for (AbstractExpression arg : contents) {
            if (first) {
                first = false;
            } else {
                buf.append(separator);
            }
            if (arg.getType() == Type.Sequence) {
                appendSeq (buf, arg.getSubs(), separator);
            } else {
                buf.append (arg);
            }
        }
        return first;
    }

    public AbstractExpression accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        return visitor.visit(this);
    }
    
    @Override 
    public boolean isDocumentOrdered () {
        return subs.length < 2 && super.isDocumentOrdered();
    }
}
