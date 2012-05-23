/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xpath;

import java.math.BigDecimal;

import lux.ExpressionVisitor;
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
        
    /**
     * @return 100
     */
    @Override public int getPrecedence () {
        return 100;
    }

    public ValueType getValueType () {
        return valueType;
    }

    public Object getValue() {
        return value;
    }
    
    @Override
    public void toString(StringBuilder buf) {
        if (value == null) {
            buf.append ("()");
        }
        else if (valueType == ValueType.STRING) {
            escapeString(value.toString(), buf);
        }
        else if (valueType == ValueType.BOOLEAN) {
            buf.append (value).append("()");
        }
        else if (value instanceof Double) {
            Double d = (Double) value;
            if (d.isInfinite()) {
                if (d > 0)
                    buf.append ("xs:float('INF')");
                else
                    buf.append ("xs:float('-INF')");
            }
            else if (d.isNaN()) {
                buf.append ("xs:float('NaN')");
            }
            else {
                buf.append (d);
            }
        }
        else {
            buf.append (value);
        }
    }

    public static void escapeString(String s, StringBuilder buf) {
        buf.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
            case '"': buf.append ("\"\""); break;
            case '&': buf.append ("&amp;"); break;
            default: buf.append (c);
            }
        }
        buf.append('"');
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
