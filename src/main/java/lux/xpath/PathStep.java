package lux.xpath;

public class PathStep extends AbstractExpression {
    public enum Axis {

        Self("self", true), Child("child", true), Parent("parent", false), 
            Descendant("descendant", true), DescendantSelf("descendant-or-self", true),
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

    public String toString () {
        return axis.toString() + "::" + nodeTest.toString();
    }
    
    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }
    
    @Override
    public boolean isDocumentOrdered () {
        return axis.isForward;
    }
    
}