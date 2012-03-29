package lux.api;

public enum ValueType {
    VALUE(false, false), DOCUMENT(true, false), NODE(true, false), ELEMENT(true, false), ATTRIBUTE(true, false), 
        TEXT(true, false), ATOMIC(false, true), STRING(false, true), INT(false, true), NUMBER(false, true);

    public final boolean isNode;
    public final boolean isAtomic;

    ValueType(boolean isNode, boolean isAtomic) {
        this.isNode = isNode;
        this.isAtomic = isAtomic;
    }
    
    /**
     * We treat DOCUMENT as a basically "untyped"
     * because we only really care about whether we need to select sub-documents
     * FIXME: rename this function (or the DOCUMENT type?) to express its real purpose (not a type restriction)
     * 
     * @param other another type
     * @return whether this type is a subtype of the other
     */
    public boolean is (ValueType other) {
        if (this == other)
            return true;
        if (other == VALUE)
            return true;
        if (this.isAtomic)
            return other == ATOMIC || other == DOCUMENT;
        if (this.isNode)
            return other == NODE || other == DOCUMENT;            
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
}