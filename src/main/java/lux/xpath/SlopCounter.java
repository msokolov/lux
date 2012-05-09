package lux.xpath;

import lux.api.ValueType;
import lux.xpath.PathStep.Axis;

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
        if (slop == null) {
            slop = 0;
        }
        return root;
    }

    @Override
    public AbstractExpression visit(PathStep step) {
        if (done) {
            return step;
        }
        NodeTest nodeTest = step.getNodeTest();
        Axis axis = step.getAxis();
        if (axis == Axis.Child) {
            if ((nodeTest.getType ().equals (ValueType.NODE)
                 || nodeTest.getType().equals (ValueType.ELEMENT))
                 )
            {
                if (slop == null) {
                    slop = 0;
                }
                if (nodeTest.getQName() == null) {
                    ++slop;
                }
            }
        } else if (axis == Axis.Descendant || axis == Axis.DescendantSelf) {
            // A number bigger than any document would ever be nested?  A
            // document nested this deeply would likely cause other
            // problems.  Surround Query Parser can only parse 2-digit distances
            slop = 98;
        } else {
            done = true;
        }
        return step;
    }

    @Override
    public AbstractExpression visit(FunCall f) {
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
        // FIXME: handle /a/(b|c)/d ?
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