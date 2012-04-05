package lux.xquery;

public class PathStep extends Aex {
    public enum Axis {
        private final String name;

        Self("self"), Child("child"), Parent("parent"), 
            Descendant("descendant"), DescendantSelf("descendant-or-self"),
            Ancestor("ancestor"), AncestorSelf("ancestor-or-self"), 
            Preceding("preceding"), Following("following"),
            PrecedingSibling("preceding-sibling"), FollowingSibling("following-sibling"),
            Attribute("attribute");

        Axis (String name) {
            this.name = name;            
        }

        public String toString() {
            return name;
        }
    };

    private final Axis axis;
    private final NodeTest nodeTest;

    public PathStep (Axis axis, NodeTest nodeTest) {
        this.axis = axis;
        this.nodeTest = nodeTest;
        type = Type.PathStep;
    }

    public Axis getAxis () {
        return axis;
    }

    public NodeTest getNodeTest () {
        return nodeTest; 
    }

    public String toString () {
        return axis.toString() + "::" + nodeTest.toString();
    }
}