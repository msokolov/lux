/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

package lux.xpath;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import lux.ExpressionVisitor;
import lux.api.LuxException;
import lux.api.ValueType;

import com.sun.org.apache.xml.internal.security.utils.Base64;

public class LiteralExpression extends AbstractExpression {
    
    private Object value;
    private ValueType valueType;
    
    public LiteralExpression () {
        this (null);
    }
    
    public LiteralExpression (Object value, ValueType valueType) {
        super(Type.Literal);
        this.value = value;
        this.valueType = valueType;
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
    
    @Override
    public void toString(StringBuilder buf) {
        if (value == null) {
            buf.append ("()");
            return;
        }
        switch (valueType) {
        case ATOMIC:
            buf.append ("xs:untypedAtomic(");
            escapeString (value.toString(), buf);
            buf.append (')');
            break;
        case STRING:
            escapeString(value.toString(), buf);
            break;
        case BOOLEAN:
            buf.append (value).append("()");
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
            /*
        case DATE:
            buf.append("xs:date(\"").append (calendarToXsDate(getCalendar())).append("\")");
            break;
            */
        
        case DATE_TIME:
            buf.append("xs:dateTime(\"").append (calendarToXsDateTime(getCalendar())).append("\")");
            break;
        case TIME:
            buf.append("xs:time(\"").append (value).append("\")");
            break;
        case DECIMAL:
            buf.append("xs:decimal(").append (value).append(")");
            break;
        case HEX_BINARY:
            buf.append("xs:hexBinary(\"");
            appendHex(buf, (byte[])value);
            buf.append("\")");
            break;
        case BASE64_BINARY:
            buf.append("xs:base64Binary(\"");
            buf.append(Base64.encode((byte[])value));
            buf.append("\")");
            break;
        default:
            // rely on the object's toString method
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

    private Calendar getCalendar() {
        Calendar dt = Calendar.getInstance();
        dt.setTimeZone(TimeZone.getTimeZone("UT"));
        dt.setTime((Date) value);
        return dt;
    }
    
    public static String calendarToXsDateTime (Calendar cal)
    {
        String tz="";
        int offset = cal.get(Calendar.ZONE_OFFSET);
        String sgn;
        if (offset < 0) {
            sgn = "-";
            offset = -offset;
        } else {
            sgn = "+";
        }
        int minutes = offset / 60000;
        int hours = minutes / 60;
        minutes = minutes % 60;
        tz = String.format ("%s%02d:%02d", sgn, hours, minutes);
        return String.format("%04d-%02d-%02dT%02d:%02d:%02d%s",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                cal.get(Calendar.SECOND),
                tz
        );
    }

    public static String calendarToXsDate(Calendar cal)
    {
        return String.format("%04d-%02d-%02dZ",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
        );
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
