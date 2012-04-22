package lux.xpath;

import java.math.BigDecimal;

import lux.api.LuxException;
import lux.api.ValueType;

public class LiteralExpression extends AbstractExpression {
    
    private Object value;
    private ValueType valueType;
    
    public LiteralExpression () {
        this (null);
    }
    
    public LiteralExpression (Object value) {
        super(Type.Literal);
        this.value = value;
        if (value != null) {
            valueType = computeType (value);
        } else {
            valueType = ValueType.VALUE;
        }
    }

    public static final LiteralExpression ONE = new LiteralExpression (1);
    
    private static ValueType computeType (Object value) {
        // TODO: date, dateTime, duration
        if (value instanceof String) {
            return ValueType.STRING;
        } else if (value instanceof Integer || value instanceof Long) {
            return ValueType.INT;
        } else if (value instanceof Double || value instanceof Float || value instanceof BigDecimal) {
            return ValueType.NUMBER;
        } else if (value instanceof Boolean) {
            return ValueType.BOOLEAN;
        }
        throw new LuxException ("unsupported java object type: " + value.getClass().getSimpleName());
    }
        
    public ValueType getValueType () {
        return valueType;
    }

    public Object getValue() {
        return value;
    }
    
    @Override
    public String toString() {
        if (value == null) {
            return "()";
        }
        if (valueType == ValueType.STRING) {
            return '"' + value.toString().replace ("\"", "\"\"") + '"';
        }
        if (valueType == ValueType.BOOLEAN) {
            return value.toString() + "()";
        }
        return value.toString();
    }

    public AbstractExpression accept(ExpressionVisitor visitor) {
        return visitor.visit(this);
    }
    
    @Override 
    public boolean equals (Object other) {
        if (other instanceof LiteralExpression) {
            return value.equals(((LiteralExpression)other).value);
        }
        return false;
    }
    
    @Override
    public int hashCode () {
        return value.hashCode();
    }
}
