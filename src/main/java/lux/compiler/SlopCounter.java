package lux.compiler;

import lux.xml.ValueType;
import lux.xpath.AbstractExpression;
import lux.xpath.BinaryOperation;
import lux.xpath.ExpressionVisitorBase;
import lux.xpath.FunCall;
import lux.xpath.LiteralExpression;
import lux.xpath.NodeTest;
import lux.xpath.PathStep;
import lux.xpath.Root;
import lux.xpath.Sequence;

/**
 * adds up the number of wildcard ("*" or node()) path steps on the left or
 * right-hand side of a path; descendant axis steps count as an infinite
 * number of wildcard steps.
 
 * The path distance underlying this function expresses the "vertical"
 * distance between two sets of nodes.  This is really only defined for
 * expressions separated by some combination of self, child, and
 * descendant steps, and it counts the number of wildcard (*, node())
 * steps.
 */

public class SlopCounter extends ExpressionVisitorBase {

    private boolean done = false;
    private Integer slop = null;

    /**
     * reset back to the initial state so the counter may be reused.
     */
    public void reset() {
        slop = null;
        done = false;
    }

    @Override
    public AbstractExpression visit(Root root) {
        foundNode();
        return root;
    }

    @Override
    public AbstractExpression visit(PathStep step) {
        if (done) {
            // FIXME: tests never hit this?  There might be some confused logic
            // The idea was that once you hit a named (non-wild) node, you can terminate the visit
            // since you've counted the width of the gap.
            return step;
        }
        NodeTest nodeTest = step.getNodeTest();
        switch (step.getAxis()) {
        case Child:
            if ((nodeTest.getType().equals(ValueType.NODE) || nodeTest.getType().equals(ValueType.ELEMENT))) {
                foundNode();
                if (nodeTest.isWild()) {
                    ++slop;
                } else {
                    done = true;
                }
            } // else? done?
            break;
        case Self:
            if ((nodeTest.getType().equals(ValueType.NODE) || nodeTest.getType().equals(ValueType.ELEMENT))) {
                foundNode();
                if (!isReverse()) {
                    --slop; // self:: matches an adjacent wildcard *on the left* and closes up a gap
                }
                // however - multiple self:: in sequence ??
            } // else? done?
            break;
        case Descendant:
        case DescendantSelf:
            if (isReverse()) {
                // we're going right-to-left
                if (slop == null && !nodeTest.isWild()) {
                    // we see our first thing and it's a named node
                    slop = 0;
                    done = true;
                } else {
                    slop = 98;
                }
            } else {
                // A number bigger than any document would ever be nested?  A
                // document nested this deeply would likely cause other
                // problems.  Surround Query Parser can only parse 2-digit distances
                slop = 98;
            }
            break;
        case Attribute:
            if (nodeTest.getQName() != null) {
                foundNode();
            }
            break;
        default:
            done = true;
            break;
        }
        return step;
    }

    private void foundNode() {
        if (slop == null) {
            slop = 0;
        }
    }

    @Override
    public AbstractExpression visit(FunCall f) {
        if (! (f.getName().equals(FunCall.FN_EXISTS) || f.getName().equals(FunCall.FN_DATA))) {
            // We can infer a path relationship with exists() and data() because they are 
            // existence-preserving.  We should also be able to invert not(exists()) and 
            // empty(), and not(), etc. in the path index case.
            slop = null;
        }
        done = true;
        return f;
    }

    @Override
    public AbstractExpression visit(LiteralExpression lit) {
        done = true;
        return lit;
    }

    @Override
    public AbstractExpression visit(Sequence seq) {
        done = true;
        return seq;
    }

    @Override
    public AbstractExpression visit(BinaryOperation exp) {
        done = true;
        return exp;
    }

    public Integer getSlop () {
        return slop;
    }
    
    @Override
    public boolean isDone () {
        return done;
    }


}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
