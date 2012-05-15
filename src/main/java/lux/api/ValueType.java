package lux.api;

/** A lux-specific type system.  These types correspond roughly to the XDM / XML Schema types
 * but only include types that are (or may) be useful for optimiization, and there are some extensions,
 * like BOOLEAN_FALSE, which is just like boolean, but takes on the opposite sense: BOOLEAN_FALSE.true==!BOOLEAN.true.
 * @author sokolov
 *
 */
public enum ValueType {
    
    VALUE(false), DOCUMENT("document-node"), NODE("node"), ELEMENT("element"), ATTRIBUTE("attribute"), 
        TEXT("text"), COMMENT("comment"), PROCESSING_INSTRUCTION("processing-instruction"),
        ATOMIC(true), STRING(true), INT(true), NUMBER(true), BOOLEAN(true), BOOLEAN_FALSE(true);

    public final boolean isNode;
    public final boolean isAtomic;
    public final String nodeTest;

    ValueType(String nodeTest) {
        this.isNode = true;
        this.isAtomic = false;
        this.nodeTest = nodeTest;
    }

    ValueType(boolean isAtomic) {
        this.isAtomic = isAtomic;
        isNode = false;
        nodeTest = null;
    }
    
    /**
     * @param other another type
     * @return whether this type is a subtype of the other
     */
    public boolean is (ValueType other) {
        if (this == other)
            return true;
        if (other == VALUE)
            return true;
        if (this.isAtomic)
            return other == ATOMIC;
        if (this.isNode)
            return other == NODE;            
        return false;
    }

    public ValueType restrict(ValueType type) {
        if (this.is (type)) {
            return this;
        }
        if (type.is(this)) {
            return type;
        }
        if (type.isNode && this.isNode) {
            return NODE;
        }
        if (type.isAtomic && this.isAtomic) {
            return ATOMIC;
        }
        return VALUE;
    }
    
    /**
     * @return the most specific type that includes both this and the other type.
     * @param type the other type
     */
    public ValueType promote(ValueType type) {
        if (this == type)
            return this;
        if (isNode && type.isNode)
            return ValueType.NODE;
        if (isAtomic && type.isAtomic)
            return ValueType.ATOMIC;
        return ValueType.VALUE;
    }
}