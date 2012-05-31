package lux.api;

/** A lux-specific type system.  These types correspond roughly to the XDM / XML Schema types
 * but only include types that are (or may) be useful for optimiization, and there are some extensions,
 * like BOOLEAN_FALSE, which is just like boolean, but takes on the opposite sense: BOOLEAN_FALSE.true==!BOOLEAN.true.
 * @author sokolov
 *
 */
public enum ValueType {
    
    VALUE("value", false, false), 
    DOCUMENT("document-node"), 
    NODE("node"), 
    ELEMENT("element"), 
    ATTRIBUTE("attribute"), 
    TEXT("text"), 
    COMMENT("comment"), 
    PROCESSING_INSTRUCTION("processing-instruction"),
        
    ATOMIC("xs:untypedAtomic", true), 
    STRING("xs:string", true), 
    INT("xs:int", true), 
    // NUMBER("xs:integer", true), 
    BOOLEAN("xs:boolean", true), 
    BOOLEAN_FALSE("xs:boolean", true), 
    DATE("xs:date", true), 
    DATE_TIME("xs:dateTime", true),
    DAY("xs:gDay", true),
    MONTH_DAY("xs:gMonthDay", true),
    YEAR("xs:gYear", true),
    YEAR_MONTH("xs:gYearMonth", true),    
    FLOAT("xs:float", true), 
    DOUBLE("xs:double", true), 
    DECIMAL("xs:decimal", true), 
    TIME("xs:time", true), 
    HEX_BINARY("xs:hexBinary", true), 
    BASE64_BINARY("xs:base64Binary", true), 
    MONTH("xs:gMonth", true), 
    UNTYPED_ATOMIC("xs:untypedAtomic", true);

    public final boolean isNode;
    public final boolean isAtomic;
    public final String name;

    ValueType(String nodeTest) {
        this.isNode = true;
        this.isAtomic = false;
        this.name = nodeTest;
    }

    ValueType(String typeName, boolean isAtomic) {
        this.isAtomic = isAtomic;
        isNode = false;
        name = typeName;
    }
    
    ValueType(String typeName, boolean isAtomic, boolean isNode) {
        this.isAtomic = isAtomic;        
        this.isNode = isNode;
        name = typeName;
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

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
