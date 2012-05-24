/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xpath;

import lux.ExpressionVisitor;

public class PathStep extends AbstractExpression {
    public enum Axis {

        Self("self", true), Child("child", true), Parent("parent", false), 
            Descendant("descendant", true), DescendantSelf("descendant-or-self", false),
            Ancestor("ancestor", false), AncestorSelf("ancestor-or-self", false), 
            Preceding("preceding", false), Following("following", true),
            PrecedingSibling("preceding-sibling", false), FollowingSibling("following-sibling", true),
            Attribute("attribute", true);

        public final String name;
        public final boolean isForward;

        Axis (String name, boolean forward) {
            this.name = name;
            this.isForward = forward;
        }

        public String toString() {
            return name;
        }
    };

    private final Axis axis;
    private final NodeTest nodeTest;

    public PathStep (Axis axis, NodeTest nodeTest) {
        super (Type.PathStep);
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

}