package lux.xpath;

import lux.api.ValueType;

public class NodeTest {

    private final ValueType type;

    private final QName name;

    /** @param type the type of test: one of DOCUMENT, NODE, ELEMENT,
     * ATTRIBUTE, TEXT, PROCESSING_INSTRUCTION or COMMENT.
     *
     * @param name the name of the node that the test matches, or null
     * if any node of the given type matches.
     *
     * <p> The local * name may be "*", indicating a match to any local
     * name.
     *
     * <p> The prefix may be "*" to indicate any namespace matches (in
     * which case the namespace uri is ignored and should be null).
     *
     * <p> The namespace uri is preserved for use in query generation,
     * however it is the caller's responsibility to ensure that namespace
     * prefix bindings are consistent within an expression tree, and with
     * the surrounding environment.
     * </p>
     *
     * <p> For node tests without names (node(), comment(), text()), the
     * name is ignored and should be null.  For node tests without namespaces
     * (processing-instruction()), the namespace uri and prefix are both
     * ignored and should be null.
     *
     * @throws IllegalArgumentException if the type is not a node type,
     * @throws NullPointerException if the type is null
     */
    public NodeTest (ValueType type, QName name) {
        if (! type.isNode) {
            throw new IllegalArgumentException ("Attempt to construct a NodeTest with type " + 
                                                type + " which is not a type of node");
        }
        this.type = type;
        this.name = name;
    }

    public NodeTest (ValueType type) {
        this (type, null);
    }

    public ValueType getType () {
        return type;
    }

    public String toString () {
        switch (type) {
        case NODE: case COMMENT: case TEXT:
            return type.nodeTest + "()";
        case ELEMENT: case ATTRIBUTE:
            if (name == null) {
                return type.nodeTest + "()";
            }
            return type.nodeTest + '(' + name.toString() + ')';
        case DOCUMENT:
            if (name == null) {
                return type.nodeTest + "()";
            }
            return type.nodeTest + '(' + name.toString() + ')';        
        case PROCESSING_INSTRUCTION:
            if (name == null) {
                return type.nodeTest + "()";
            }
            return type.nodeTest + '(' + name.getLocalPart() + ')';
        default:
            throw new IllegalArgumentException ("invalid node type " + type);
        }
    }
}
