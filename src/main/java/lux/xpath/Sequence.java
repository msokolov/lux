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
    
    /**
     * @param separator 
     * @param contents
     * @return a string joining all the contents in order, separated from each other by the separator, 
     * with the entire Sequence wrapped in "( )". If any contents are Sequences, these are merged together
     * into a single flattened sequence.
     */
    static final String seqAsString (String separator, AbstractExpression ... contents) {
        StringBuilder buf = new StringBuilder ();
        buf.append('(');
        appendSeqContents(buf, contents, separator);
        buf.append (')');
        return buf.toString();
    }

    static void appendSeqContents(StringBuilder buf, AbstractExpression[] contents, String separator) {
        if (contents.length > 0) {
            appendSeqItem(buf, separator, contents[0]);
        }
        for (int i = 1; i < contents.length; i++) {
            buf.append(separator);
            appendSeqItem(buf, separator, contents[i]);
        }
    }

    private static void appendSeqItem(StringBuilder buf, String separator, AbstractExpression arg) {
        if (arg.getType() == Type.Sequence) {
            appendSeqContents (buf, arg.getSubs(), separator);
        } else {
            buf.append (arg);
        }
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
