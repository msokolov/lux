package lux.xpath;

public class PathStep extends AbstractExpression {
    public enum Axis {

        Self("self"), Child("child"), Parent("parent"), 
            Descendant("descendant"), DescendantSelf("descendant-or-self"),
            Ancestor("ancestor"), AncestorSelf("ancestor-or-self"), 
            Preceding("preceding"), Following("following"),
            PrecedingSibling("preceding-sibling"), FollowingSibling("following-sibling"),
            Attribute("attribute");

        private final String name;

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

    public String toString () {
        return axis.toString() + "::" + nodeTest.toString();
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }
    
}