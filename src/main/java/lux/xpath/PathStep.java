package lux.xpath;


public class PathStep extends AbstractExpression {
    private static final int MSELF = 1;
    private static final int MCHILD = 2;
    private static final int MPARENT = 4;
    private static final int MDESCENDANT = 8;
    private static final int MANCESTOR = 16;
    private static final int MPRECEDING = 32;
    private static final int MFOLLOWING = 64;
    private static final int MPRECEDING_SIB = 128;
    private static final int MFOLLOWING_SIB = 256;
    private static final int MATTRIBUTE = 512;

    public enum Axis {

        Self("self", true, MSELF),
        Child("child", true, MCHILD),
        Parent("parent", false, MPARENT), 
        Descendant("descendant", true, MDESCENDANT | MCHILD),
        DescendantSelf("descendant-or-self", false, MDESCENDANT | MCHILD | MSELF),
        Ancestor("ancestor", false, MANCESTOR | MPARENT),
        AncestorSelf("ancestor-or-self", false, MANCESTOR | MPARENT | MSELF), 
        Preceding("preceding", false, MPRECEDING | MPRECEDING_SIB),
        Following("following", true, MFOLLOWING | MFOLLOWING_SIB),
        PrecedingSibling("preceding-sibling", false, MPRECEDING_SIB), 
        FollowingSibling("following-sibling", true, MFOLLOWING_SIB),
        Attribute("attribute", true, MATTRIBUTE);

        public final String name;
        public final boolean isForward;
        public final int rangeMask;

        Axis (String name, boolean forward, int rangeMask) {
            this.name = name;
            this.isForward = forward;
            this.rangeMask = rangeMask;
        }

        @Override
        public String toString() {
            return name;
        }
    };

    private final Axis axis;
    private final NodeTest nodeTest;

    public PathStep (Axis axis, NodeTest nodeTest) {
        super (Type.PATH_STEP);
        this.axis = axis;
        this.nodeTest = nodeTest;
    }

    public Axis getAxis () {
        return axis;
    }

    public NodeTest getNodeTest () {
        return nodeTest; 
    }

    @Override
    public void toString (StringBuilder buf) {
        buf.append (axis).append("::");
        nodeTest.toString (buf);
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

    @Override
    public boolean isDocumentOrdered () {
        return axis.isForward;
    }
    
    @Override
    public AbstractExpression getLastContextStep () {
        // If self::* or self::node(), return Dot instead
        if (axis == Axis.Self && nodeTest.isWild()) {
            return Dot.getInstance();
        }
        return this;
    }

    @Override
    public boolean propEquals (AbstractExpression other) {
        return axis == ((PathStep) other).axis &&
            nodeTest.equivalent(((PathStep) other).nodeTest);
    }

    public boolean propGreaterEqual (AbstractExpression other) {
    	PathStep otherStep = (PathStep) other;
        int oax = otherStep.axis.rangeMask;
        return (axis == otherStep.axis ||
        		((axis.rangeMask & oax) == oax))
            && nodeTest.propGreaterEqual(otherStep.nodeTest);
    }
    
    @Override
    public int equivHash () {
    	return axis.ordinal() * nodeTest.equivHash();
    }

    @Override
    public boolean isRestrictive () {
        return true;
    }

}

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
