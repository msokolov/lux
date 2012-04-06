package lux.xpath;

import lux.api.ValueType;

public class LiteralExpression extends AbstractExpression {
    
    private Object value;
    private ValueType valueType;
    
    public LiteralExpression (String value) {
        super (Type.Literal);
        this.value = value;
        valueType = ValueType.STRING;
    }
    
    public LiteralExpression (Integer value) {
        super (Type.Literal);
        this.value = value;
        valueType = ValueType.INT;
    }
    
    public LiteralExpression (Double value) {
        super (Type.Literal);
        this.value = value;
        valueType = ValueType.NUMBER;
    }
    
    public ValueType getValueType () {
        return valueType;
    }

    @Override
    public String toString() {
        if (valueType == ValueType.STRING) {
            return '"' + value.toString().replace ("\"", "\"\"") + '"';
        }
        return value.toString();
    }

}
