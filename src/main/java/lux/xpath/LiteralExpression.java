/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xpath;

import java.math.BigDecimal;
import java.util.Date;

import lux.ExpressionVisitor;
import lux.api.LuxException;
import lux.api.ValueType;

import javax.xml.bind.DatatypeConverter;

public class LiteralExpression extends AbstractExpression {
    
    private final Object value;
    private final ValueType valueType;
    private final String xqTypeName; // a built-in xquery type name, like xs:integer
    
    public LiteralExpression () {
        this (null);
    }
    
    public LiteralExpression (Object value, ValueType valueType, String xqTypeName) {
        super(Type.Literal);
        this.value = value;
        this.valueType = valueType;
        this.xqTypeName = xqTypeName;
    }

    public LiteralExpression (Object value, ValueType valueType) {
        super(Type.Literal);
        this.value = value;
        this.valueType = valueType;
        this.xqTypeName = valueType.name;
    }

    public LiteralExpression (Object value) {
        super(Type.Literal);
        this.value = value;
        if (value != null) {
            valueType = computeType (value);
        } else {
            valueType = ValueType.VALUE;
        }
        this.xqTypeName = valueType.name;
    }

    public static final LiteralExpression ONE = new LiteralExpression (1);
    
    private static ValueType computeType (Object value) {
        // TODO: duration
        if (value instanceof String) {
            return ValueType.STRING;
        } else if (value instanceof Integer || value instanceof Long) {
            return ValueType.INT;
        } else if (value instanceof Double) {
            return ValueType.DOUBLE;
        } else if (value instanceof Float) {
            return ValueType.FLOAT;
        } else if (value instanceof BigDecimal) {
            return ValueType.DECIMAL;
        } else if (value instanceof Boolean) {
            return ValueType.BOOLEAN;
        } else if (value instanceof Date) {
            return ValueType.DATE;
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
    
    public String getXQueryTypeName() {
        return xqTypeName;
    }
    
    @Override
    public void toString(StringBuilder buf) {
        if (value == null) {
            buf.append ("()");
            return;
        }
        switch (valueType) {
        case UNTYPED_ATOMIC:
            buf.append ("xs:untypedAtomic(");
            escapeString (value.toString(), buf);
            buf.append (')');
            break;
        case STRING:
            escapeString (value.toString(), buf);
            break;
        case BOOLEAN:
            buf.append ("fn:").append(value).append("()");
            break;
        case FLOAT:
            Float f = (Float) value;
            if (f.isInfinite()) {
                if (f > 0)
                    buf.append ("xs:float('INF')");
                else
                    buf.append ("xs:float('-INF')");
            }
            else if (f.isNaN()) {
                buf.append ("xs:float('NaN')");
            }
            else {
                buf.append ("xs:float(").append(f).append(')');
            }
            break;
        case DOUBLE:
            Double d = (Double) value;
            if (d.isInfinite()) {
                if (d > 0)
                    buf.append ("xs:double('INF')");
                else
                    buf.append ("xs:double('-INF')");
            }
            else if (d.isNaN()) {
                buf.append ("xs:double('NaN')");
            }
            else {
                buf.append ("xs:double(").append(d).append(')');
            }
            break;

        case DECIMAL:            
            buf.append("xs:decimal(").append (((BigDecimal)value).toPlainString()).append(")");
            break;
        case HEX_BINARY:
            buf.append("xs:hexBinary(\"");
            appendHex(buf, (byte[])value);
            buf.append("\")");
            break;
        case BASE64_BINARY:
            buf.append("xs:base64Binary(\"");
            buf.append(DatatypeConverter.printBase64Binary((byte[])value));
            buf.append("\")");
            break;
        
        case DATE:
        case DATE_TIME:
        case TIME:
        case DAY:
        case MONTH:
        case MONTH_DAY:
        case YEAR:
        case YEAR_MONTH:
        case ATOMIC:
            buf.append(xqTypeName).append("(\"").append(value).append("\")");
            break;
            
        case QNAME:
            buf.append("fn:QName(");
            escapeString(((QName)value).getNamespaceURI(), buf);
            buf.append (",\"");
            ((QName)value).toString(buf);
            buf.append("\")");
            break;
            
        default:
            // rely on the object's toString method - is it only xs:int and its ilk that do this?
            buf.append (value);
        }
    }

    private static char hexdigits[] = new char[] { '0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F' };

    private void appendHex(StringBuilder buf, byte[] bytes) {
        for (byte b : bytes) {
            int b1 = ((b & 0xF0) >> 4);
            buf.append (hexdigits[b1]);
            int b2 = b & 0xF;
            buf.append (hexdigits[b2]);
        }
    }
    
    public static void escapeString(String s, StringBuilder buf) {
        buf.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
            case '"': buf.append ("\"\""); break;
            case '&': buf.append ("&amp;"); break;
            case '\r': buf.append("&#xD;"); break;  // XML line ending normalization removes these unless they come in as character references
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

/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */
