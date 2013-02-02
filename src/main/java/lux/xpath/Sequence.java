package lux.xpath;


public class Sequence extends AbstractExpression {
    
    public Sequence (AbstractExpression ... contents) {
        super (Type.SEQUENCE);
        setSubs (contents);
    }
    
    @Override
    public void toString(StringBuilder buf) {
        seqAsString(buf, subs);
    }
    
    /**
     * append all the contents in order, separated from each other by the
     * separator, to the buffer, wrapping the sequence in "( )". If any
     * contents are Sequences, these are merged together into a single
     * flattened sequence.
     * @param buf
     * @param separator 
     * @param contents
     */
    private void seqAsString (StringBuilder buf, AbstractExpression ... contents) {
        buf.append('(');
        appendSeqContents(buf, contents, ",", getPrecedence());
        buf.append (')');
    }

    static void appendSeqContents(StringBuilder buf, AbstractExpression[] contents, String separator, int precedence) {
        if (contents.length > 0) {
            appendSeqItem(buf, separator, precedence, contents[0]);
        }
        for (int i = 1; i < contents.length; i++) {
            buf.append(separator);
            appendSeqItem(buf, separator, precedence, contents[i]);
        }
    }

    private static void appendSeqItem(StringBuilder buf, String separator, int precedence, AbstractExpression arg) {
        if (arg.getType() == Type.SEQUENCE) {
            appendSeqContents (buf, arg.getSubs(), separator, precedence);
        } else {
            if (arg.getPrecedence() < precedence) {
                buf.append ('(');
                arg.toString(buf);
                buf.append (')');
            } else {
                arg.toString(buf);
            }
        }
    }

    @Override
    public AbstractExpression accept(ExpressionVisitor visitor) {
        super.acceptSubs(visitor);
        return visitor.visit(this);
    }
    
    @Override 
    public boolean isDocumentOrdered () {
        return subs.length < 2 && super.isDocumentOrdered();
    }

    /**
     * @return 1, the precedence of the comma operator.
     */
    @Override
    public int getPrecedence () {
        return 1;
    }
}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
