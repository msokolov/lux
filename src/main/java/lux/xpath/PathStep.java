package lux.xpath;


public class PathStep extends AbstractExpression {
    public static final int MSELF = 1;
    public static final int MCHILD = 2;
    public static final int MPARENT = 4;
    public static final int MDESCENDANT = 8;
    public static final int MANCESTOR = 16;
    public static final int MPRECEDING = 32;
    public static final int MFOLLOWING = 64;
    public static final int MPRECEDING_SIB = 128;
    public static final int MFOLLOWING_SIB = 256;
    public static final int MATTRIBUTE = 512;

    public enum Axis {

        DescendantSelf("descendant-or-self", false, MDESCENDANT | MCHILD | MSELF),
        Descendant("descendant", true, MDESCENDANT | MCHILD, DescendantSelf),
        AncestorSelf("ancestor-or-self", false, MANCESTOR | MPARENT | MSELF), 
        Ancestor("ancestor", false, MANCESTOR | MPARENT, AncestorSelf),
        Self("self", true, MSELF, AncestorSelf, DescendantSelf),
        Child("child", true, MCHILD, Descendant),
        Parent("parent", false, MPARENT, Ancestor), 
        Preceding("preceding", false, MPRECEDING | MPRECEDING_SIB),
        Following("following", true, MFOLLOWING | MFOLLOWING_SIB),
        PrecedingSibling("preceding-sibling", false, MPRECEDING_SIB, Preceding), 
        FollowingSibling("following-sibling", true, MFOLLOWING_SIB, Following),
        Attribute("attribute", true, MATTRIBUTE);

        public final String name;
        public final boolean isForward;
        public final int rangeMask;
        public Axis [] extensions;

        Axis (String name, boolean forward, int rangeMask, Axis ... extensions) {
            this.name = name;
            this.isForward = forward;
            this.rangeMask = rangeMask;
            this.extensions = extensions;
        }

        @Override
        public String toString() {
            return name;
        }
        
        public boolean isAxisMask (int mask) {
        	return (rangeMask & mask) != 0;
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
